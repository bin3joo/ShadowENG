"""종합 평가 오케스트레이터.

유저 오디오에 대해 7대 지표를 산출하고 가중 종합 점수를 계산합니다.
``pipeline.StyleEchoPipeline.evaluate()`` 에서 추출된 순수 채점 로직입니다.
"""

import logging
import os
import tempfile
from typing import Any

import config
import jiwer
import librosa
import numpy as np
from domain.processing.audio_processing import (
    peak_normalize_audio,
    separate_vocals,
)

logger = logging.getLogger(__name__)
from domain.processing.engine_utils import (
    _REMOVE_PUNCT,
    _canonicalize_tokens,
    _sum_word_durations,
    count_pauses_from_words,
)
from domain.prosody.feature_extraction import (
    _normalize_f0,
    _normalize_rms,
)
from domain.scoring.boundary_scoring import analyze_boundary_tone
from domain.scoring.dynamic_scoring import analyze_dynamic_stress
from domain.scoring.pause_scoring import analyze_pause_alignment
from domain.scoring.pitch_contour import analyze_word_pitch_contour
from domain.scoring.prosody_scoring import analyze_prosody
from domain.scoring.rhythm_scoring import analyze_word_rhythm
from domain.scoring.word_alignment import align_user_words_to_ref
from scipy.io import wavfile





def _apply_user_vr(
    y: np.ndarray,
    sr: int,
) -> np.ndarray:
    """유저 오디오에 VR(보컬 분리)을 적용하여 깨끗한 보컬만 반환합니다.

    오디오 배열을 임시 WAV 파일로 저장 → ``separate_vocals`` 호출 →
    분리된 보컬을 다시 numpy 배열로 로드하는 과정을 캡슐화합니다.

    VR 모델 누락 등으로 분리에 실패하면 원본 배열을 그대로 반환합니다.

    Args:
        y: 입력 오디오 배열 (float32).
        sr: 샘플레이트.

    Returns:
        보컬 분리된 오디오 배열. 실패 시 원본 ``y`` 반환.
    """
    from scipy.io import wavfile as _wavfile

    vr_tmp_dir = tempfile.mkdtemp(prefix="styleecho_user_vr_")
    vr_input_path = os.path.join(vr_tmp_dir, "user_input.wav")

    try:
        _wavfile.write(
            vr_input_path,
            sr,
            np.asarray(y, dtype=np.float32),
        )
        vocal_path = separate_vocals(vr_input_path, vr_tmp_dir)

        if vocal_path != vr_input_path:
            y_vocal, _ = librosa.load(vocal_path, sr=sr)
            logger.info(
                "User VR 적용 완료: %s → %s",
                vr_input_path,
                vocal_path,
            )
            return y_vocal

        logger.warning(
            "User VR 분리 결과가 원본과 동일합니다. "
            "원본 오디오를 사용합니다."
        )
        return y

    except Exception as exc:
        logger.warning("User VR 처리 실패 (fallback): %s", exc)
        return y

    finally:
        # 임시 파일 정리
        import shutil

        try:
            shutil.rmtree(vr_tmp_dir, ignore_errors=True)
        except OSError:
            pass


def evaluate(
    user_audio_path: str,
    ref_data: dict[str, Any],
    stt_fn: Any,
    prosody_fn: Any,
) -> dict[str, Any]:
    """유저 오디오와 레퍼런스 데이터를 비교하여 7대 지표를 산출합니다.

    Args:
        user_audio_path: 유저 오디오 파일 경로.
        ref_data: 레퍼런스 JSON 데이터 (final_script, features,
            word_timestamps, hop_length).
        stt_fn: ``extract_whisper_stats(audio_path) -> dict`` 콜백.
        prosody_fn: ``extract_prosody_features(y, sr, denoise, hop_length)
            -> (f0, rms, features)`` 콜백.

    Returns:
        평가 결과 딕셔너리 (status, scores, details 등).
    """
    ref_script = ref_data["final_script"]
    ref_text = " ".join(_canonicalize_tokens(ref_script))
    ref_word_timestamps = ref_data.get("word_timestamps", [])
    ref_f0 = np.array(ref_data.get("features", {}).get("f0_array", []))
    ref_rms = np.array(ref_data.get("features", {}).get("rms_array", []))
    hop_length = ref_data.get("hop_length")
    if hop_length is None or hop_length <= 0:
        hop_length = len(ref_f0) if len(ref_f0) > 0 else config.HOP_LENGTH

    # 0. 유저 오디오 로드 및 STT 전용 Peak 정규화
    target_sr = config.TARGET_SR
    user_y_raw, _ = librosa.load(user_audio_path, sr=target_sr)
    user_y_normalized = peak_normalize_audio(user_y_raw)

    # 정규화된 WAV를 임시 파일로 저장하여 STT에 전달
    norm_tmp = tempfile.NamedTemporaryFile(
        suffix=".wav",
        delete=False,
        prefix="styleecho_norm_",
    )
    norm_tmp_path = norm_tmp.name
    norm_tmp.close()

    wavfile.write(
        norm_tmp_path,
        target_sr,
        np.asarray(user_y_normalized, dtype=np.float32),
    )

    # 1. 유저 STT (Peak 정규화된 오디오 사용 → 인식률 향상)
    import os

    try:
        user_stats = stt_fn(norm_tmp_path)
    finally:
        try:
            os.remove(norm_tmp_path)
        except OSError:
            pass

    user_text = " ".join(_canonicalize_tokens(user_stats.get("text", "")))

    if not user_text:
        return {
            "status": "FAIL",
            "message": "음성이 인식되지 않았습니다. 다시 녹음해주세요.",
        }

    # 2. 유저 word_timestamps를 레퍼런스 구조에 맞게 통일
    aligned_user_words = align_user_words_to_ref(
        ref_word_timestamps,
        user_stats["word_timestamps"],
    )

    # 3. 단어 정확도 (WER → 지수 감쇠 스코어링)
    wer = jiwer.wer(
        _REMOVE_PUNCT(ref_text.lower()),
        _REMOVE_PUNCT(user_text.lower()),
    )
    word_score = 100.0 * np.exp(-config.WER_PENALTY * wer)

    # 4. 속도 점수 (Deadband 불감대 적용)
    ref_active_time = _sum_word_durations(ref_word_timestamps)
    user_active_time = user_stats["active_speech_sec"]

    k = config.SPEED_K
    rushing_penalty = config.SPEED_RUSHING_PENALTY
    deadband = config.SPEED_DEADBAND
    if ref_active_time <= 0 and user_active_time <= 0:
        speed_score = 100.0
    elif ref_active_time <= 0 or user_active_time <= 0:
        speed_score = 0.0
    else:
        speed_ratio = user_active_time / ref_active_time
        # 불감대 내: 100점
        if abs(speed_ratio - 1.0) <= deadband:
            speed_score = 100.0
        elif speed_ratio < 1.0:
            # 불감대 경계(1-deadband)를 1.0으로 매핑
            effective = speed_ratio / (1.0 - deadband)
            speed_score = 100.0 * (effective ** (k * rushing_penalty))
        else:
            # 불감대 경계(1+deadband)를 1.0으로 매핑
            effective = speed_ratio / (1.0 + deadband)
            speed_score = 100.0 * ((1.0 / effective) ** k)

    # 5. 멈춤 점수 (횟수 기반 + 위치 정합 F1 블렌딩)
    ref_pause_count = count_pauses_from_words(ref_word_timestamps)
    user_pause_count = user_stats["pause_count"]
    pause_diff = abs(user_pause_count - ref_pause_count)
    count_score = 100.0 * np.exp(
        -((pause_diff**2) / (2 * (config.PAUSE_SIGMA**2)))
    )

    # 위치 정합 F1 점수
    f1, pause_align_details = analyze_pause_alignment(
        ref_word_timestamps,
        aligned_user_words,
    )
    align_score = 100.0 * f1

    w = config.PAUSE_ALIGN_WEIGHT
    pause_score = (1.0 - w) * count_score + w * align_score

    # 6. 단어별 리듬 (통일된 유저 단어 사용)
    rhythm_score, word_feedback = analyze_word_rhythm(
        ref_word_timestamps,
        aligned_user_words,
        ref_active_time,
        user_active_time,
    )

    # 7. 유저 오디오 피처 추출
    #    config.EVALUATION_USER_VR_ENABLED 가 True 이면
    #    VR(보컬 분리) 후 깨끗한 보컬로 피처를 추출합니다.
    start_idx = int(user_stats["start_time"] * target_sr)
    end_idx = int(user_stats["end_time"] * target_sr)
    user_y_cropped = (
        user_y_raw[start_idx:end_idx]
        if end_idx > start_idx
        else user_y_raw
    )

    if config.EVALUATION_USER_VR_ENABLED:
        user_y_cropped = _apply_user_vr(
            user_y_cropped, target_sr
        )
        # VR 자체가 강력한 디노이징이므로 추가 denoise 불필요
        user_f0, user_rms, user_features = prosody_fn(
            user_y_cropped, target_sr, False, hop_length
        )
    else:
        user_f0, user_rms, user_features = prosody_fn(
            user_y_cropped, target_sr, True, hop_length
        )

    # 8. 억양 DTW + 세부 분석 (레퍼런스 피처 복원 및 크롭)
    if len(ref_f0) > 0 and len(ref_rms) > 0:
        # 레퍼런스 유효 발화 구간 크롭 (첫 단어 시작 ~ 마지막 단어 끝)
        # ref_data["word_timestamps"]는 이미 해당 파트의 오디오 시작점으로부터의 상대적 시간임
        if ref_word_timestamps:
            ref_start_time = ref_word_timestamps[0]["start"]
            ref_end_time = ref_word_timestamps[-1]["end"]
            ref_start_idx = int(ref_start_time * target_sr / hop_length)
            ref_end_idx = int(ref_end_time * target_sr / hop_length)
            
            ref_f0_t = ref_f0[ref_start_idx:ref_end_idx]
            ref_rms_t = ref_rms[ref_start_idx:ref_end_idx]
            
            # 만약 크롭 결과가 너무 짧으면 안전하게 원본 사용
            if len(ref_f0_t) < 5:
                ref_f0_t, ref_rms_t = ref_f0, ref_rms
        else:
            ref_f0_t, ref_rms_t = ref_f0, ref_rms

        min_len = min(len(ref_f0_t), len(ref_rms_t))
        ref_f0_t = ref_f0_t[:min_len]
        ref_rms_t = ref_rms_t[:min_len]

        ref_f0_norm = _normalize_f0(ref_f0_t)
        ref_rms_norm = _normalize_rms(ref_rms_t)
        
        ref_features = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)

        prosody_score = analyze_prosody(ref_features, user_features)
        boundary_score, boundary_details = analyze_boundary_tone(
            ref_f0_t, user_f0
        )
        dynamic_score, dynamic_details = analyze_dynamic_stress(
            ref_rms_t, user_rms
        )
    else:
        prosody_score = 100.0
        boundary_score = 100.0
        boundary_details = {
            "ref_slope": 0.0,
            "user_slope": 0.0,
            "status": "no_ref_data",
        }
        dynamic_score = 100.0
        dynamic_details = {
            "ref_dynamic_ratio": 0.0,
            "user_dynamic_ratio": 0.0,
            "status": "no_ref_data",
        }

    # 9. 가중 종합 점수
    w = config.SCORE_WEIGHTS
    weights = {
        "word": w.get("word_accuracy", 0.30),
        "prosody": w.get("prosody", 0.20),
        "rhythm": w.get("rhythm", 0.15),
        "boundary": w.get("boundary_tone", 0.10),
        "dynamic": w.get("dynamic_stress", 0.10),
        "speed": w.get("speed", 0.075),
        "pause": w.get("pause", 0.075),
    }

    total_score = (
        word_score * weights["word"]
        + prosody_score * weights["prosody"]
        + rhythm_score * weights["rhythm"]
        + boundary_score * weights["boundary"]
        + dynamic_score * weights["dynamic"]
        + speed_score * weights["speed"]
        + pause_score * weights["pause"]
    )
    rounded_total_score = round(float(total_score), 1)
    pass_fail = (
        "PASS" if rounded_total_score >= config.PASS_THRESHOLD else "FAIL"
    )

    # 10. 단어별 피치 컨투어 피드백
    if len(ref_f0) > 0:
        ref_start_offset = (
            ref_word_timestamps[0].get("start", 0.0)
            if ref_word_timestamps
            else 0.0
        )
        pitch_contour = analyze_word_pitch_contour(
            ref_f0,
            user_f0,
            ref_word_timestamps,
            aligned_user_words,
            sr=config.TARGET_SR,
            hop_length=config.HOP_LENGTH,
            ref_speech_start=ref_start_offset,
            user_speech_start=user_stats["start_time"],
        )
    else:
        pitch_contour = []

    return {
        "status": "SUCCESS",
        "pass_fail": pass_fail,
        "pass_threshold": round(float(config.PASS_THRESHOLD), 1),
        "user_transcription": user_text,
        "details": {
            "word_level_feedback": word_feedback,
            "boundary_tone_feedback": boundary_details,
            "dynamic_stress_feedback": dynamic_details,
            "pitch_contour_feedback": pitch_contour,
        },
        "scores": {
            "total_score": rounded_total_score,
            "word_accuracy": round(float(word_score), 1),
            "prosody_and_stress": round(float(prosody_score), 1),
            "word_rhythm_score": round(float(rhythm_score), 1),
            "boundary_tone_score": round(float(boundary_score), 1),
            "dynamic_stress_score": round(float(dynamic_score), 1),
            "speed_similarity": round(float(speed_score), 1),
            "pause_similarity": round(float(pause_score), 1),
        },
    }
