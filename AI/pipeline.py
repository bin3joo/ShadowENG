"""
StyleEcho Pipeline
==================
WhisperX 기반 STT, Forced Alignment, Diarization, Prosody 분석,
종합 채점(evaluate) 등 모델 의존 로직을 담당합니다.
"""

import importlib
import logging
import os
import re
import threading
from typing import Any

import jiwer
import librosa
import numpy as np
import torch

# Pyannote.audio 호환성 패치: torchaudio >= 2.1 에서 AudioMetaData 삭제 대응
import torchaudio

if not hasattr(torchaudio, "AudioMetaData"):
    torchaudio.AudioMetaData = type("AudioMetaData", (), {})

import config
import whisperx
from domain.processing.audio_processing import denoise_for_analysis
from domain.processing.engine_utils import (
    _REMOVE_PUNCT,
    _canonicalize_tokens,
    _normalize_word,
    _sum_word_durations,
    count_pauses_from_words,
    extract_pause_positions,
)
from domain.processing.quality import evaluate_caption_alignment_health
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean

logger = logging.getLogger(__name__)


def _normalize_f0(f0: np.ndarray) -> np.ndarray:
    """Normalize F0 values based on voiced regions only.

    Args:
        f0: Raw F0 feature array.

    Returns:
        Speaker-normalized F0 array.
    """
    valid_f0 = f0[f0 > 0]
    if len(valid_f0) > 0:
        return np.where(
            f0 > 0,
            (f0 - np.mean(valid_f0)) / (np.std(valid_f0) + 1e-8),
            0,
        )
    return f0


def _build_f0_gate_metrics(f0: np.ndarray) -> dict[str, float]:
    """Build simple F0 quality metrics for rule-based source gating.

    Args:
        f0: F0 feature array.

    Returns:
        Dictionary containing voiced-ratio and jump-ratio metrics.
    """
    if len(f0) == 0:
        return {
            "voiced_ratio": 0.0,
            "jump_ratio": 1.0,
        }

    valid_f0 = f0[f0 > 0]
    voiced_ratio = float(len(valid_f0) / max(len(f0), 1))

    if len(valid_f0) < 2:
        jump_ratio = 1.0 if len(valid_f0) == 1 else 0.0
    else:
        semitone_diff = np.abs(
            12.0 * np.log2(valid_f0[1:] / np.maximum(valid_f0[:-1], 1e-8))
        )
        jump_ratio = float(
            np.mean(
                semitone_diff
                > getattr(config, "REFERENCE_F0_GATE_MAX_SEMITONE_JUMP", 6.0)
            )
        )

    return {
        "voiced_ratio": voiced_ratio,
        "jump_ratio": jump_ratio,
    }


def _build_rms_gate_metrics(rms: np.ndarray) -> dict[str, float]:
    """Build simple RMS quality metrics for rule-based source gating.

    Args:
        rms: RMS feature array.

    Returns:
        Dictionary containing contrast and dropout metrics.
    """
    if len(rms) == 0:
        return {
            "contrast_db": 0.0,
            "dropout_ratio": 1.0,
        }

    rms_abs = np.abs(rms.astype(np.float32))
    p10 = float(np.percentile(rms_abs, 10))
    p90 = float(np.percentile(rms_abs, 90))
    contrast_db = float(20.0 * np.log10((p90 + 1e-8) / (p10 + 1e-8)))
    dropout_threshold = p90 * getattr(
        config, "REFERENCE_RMS_GATE_DROPOUT_FRACTION", 0.1
    )
    dropout_ratio = float(np.mean(rms_abs <= dropout_threshold))

    return {
        "contrast_db": contrast_db,
        "dropout_ratio": dropout_ratio,
    }


def select_reference_prosody_sources(
    original_f0: np.ndarray,
    original_rms: np.ndarray,
    vr_f0: np.ndarray,
    vr_rms: np.ndarray,
) -> dict[str, Any]:
    """Select reference F0 and RMS tracks with simple gating rules.

    Args:
        original_f0: F0 extracted from the original audio.
        original_rms: RMS extracted from the original audio.
        vr_f0: F0 extracted from the VR audio.
        vr_rms: RMS extracted from the VR audio.

    Returns:
        Selected prosody arrays, chosen source labels, and per-source metrics.
    """
    original_f0_metrics = _build_f0_gate_metrics(original_f0)
    vr_f0_metrics = _build_f0_gate_metrics(vr_f0)
    original_rms_metrics = _build_rms_gate_metrics(original_rms)
    vr_rms_metrics = _build_rms_gate_metrics(vr_rms)

    f0_source = "original"
    if (
        original_f0_metrics["voiced_ratio"]
        < getattr(config, "REFERENCE_F0_GATE_MIN_VOICED_RATIO", 0.5)
        and vr_f0_metrics["voiced_ratio"]
        >= original_f0_metrics["voiced_ratio"]
    ):
        f0_source = "vr"
    elif vr_f0_metrics["jump_ratio"] > getattr(
        config, "REFERENCE_F0_GATE_MAX_JUMP_RATIO", 0.2
    ):
        f0_source = "original"
    elif original_f0_metrics["jump_ratio"] > getattr(
        config, "REFERENCE_F0_GATE_MAX_JUMP_RATIO", 0.2
    ) and vr_f0_metrics["voiced_ratio"] >= getattr(
        config, "REFERENCE_F0_GATE_MIN_VOICED_RATIO", 0.5
    ):
        f0_source = "vr"

    rms_source = "original"
    if (
        original_rms_metrics["contrast_db"]
        < getattr(config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0)
        and vr_rms_metrics["contrast_db"]
        >= original_rms_metrics["contrast_db"]
    ):
        rms_source = "vr"
    elif vr_rms_metrics["dropout_ratio"] > getattr(
        config, "REFERENCE_RMS_GATE_MAX_DROPOUT_RATIO", 0.8
    ):
        rms_source = "original"
    elif vr_rms_metrics["contrast_db"] >= getattr(
        config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0
    ) and original_rms_metrics["contrast_db"] < getattr(
        config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0
    ):
        rms_source = "vr"

    return {
        "f0": vr_f0 if f0_source == "vr" else original_f0,
        "rms": vr_rms if rms_source == "vr" else original_rms,
        "f0_source": f0_source,
        "rms_source": rms_source,
        "original_f0_metrics": original_f0_metrics,
        "vr_f0_metrics": vr_f0_metrics,
        "original_rms_metrics": original_rms_metrics,
        "vr_rms_metrics": vr_rms_metrics,
    }


def _empty_stats(
    audio: np.ndarray | None = None,
    *,
    stt_method: str | None = None,
    diarization_used: bool = False,
) -> dict[str, Any]:
    """Build an empty STT result payload.

    Args:
        audio: Optional audio array associated with the result.
        stt_method: Optional STT method label.
        diarization_used: Whether diarization was applied.

    Returns:
        Empty STT statistics payload.
    """
    result: dict[str, Any] = {
        "text": "",
        "active_speech_sec": 0.0,
        "pause_count": 0,
        "start_time": 0.0,
        "end_time": 0.0,
        "word_timestamps": [],
        "audio_array": audio,
        "diarization_used": diarization_used,
    }
    if stt_method is not None:
        result["stt_method"] = stt_method
    return result


# ---------------------------------------------------------------------------
# Singleton 관리
# ---------------------------------------------------------------------------
_pipeline_instance: "StyleEchoPipeline | None" = None
_pipeline_lock = threading.Lock()


def get_pipeline(
    whisper_model_size: str = "large-v3",
    device: str | None = None,
    compute_type: str = "float16",
) -> "StyleEchoPipeline":
    """Return the singleton ``StyleEchoPipeline`` instance.

    Args:
        whisper_model_size: Whisper model size to load on first initialization.
        device: Explicit device override. If ``None``, the device is inferred.
        compute_type: Whisper compute type.

    Returns:
        Shared ``StyleEchoPipeline`` instance.
    """
    global _pipeline_instance
    if _pipeline_instance is None:
        with _pipeline_lock:
            if _pipeline_instance is None:
                if device is None:
                    device = "cuda" if torch.cuda.is_available() else "cpu"
                _pipeline_instance = StyleEchoPipeline(
                    whisper_model_size=whisper_model_size,
                    device=device,
                    compute_type=compute_type,
                )
    return _pipeline_instance


# ---------------------------------------------------------------------------
# Core Pipeline
# ---------------------------------------------------------------------------
class StyleEchoPipeline:
    """WhisperX 기반 영어 억양·발음 평가 파이프라인."""

    def __init__(
        self,
        whisper_model_size: str = "base",
        device: str = "cuda",
        compute_type: str = "float16",
    ):
        self.device = device
        self.compute_type = compute_type

        logger.info(
            "Loading WhisperX '%s' model on %s ...",
            whisper_model_size,
            device,
        )
        self.stt_model = whisperx.load_model(
            whisper_model_size,
            self.device,
            compute_type=self.compute_type,
        )

        logger.info("Loading Forced Alignment model (English) ...")
        self.align_model, self.align_metadata = whisperx.load_align_model(
            language_code="en", device=self.device
        )
        self.diarization_device = config.REFERENCE_DIARIZATION_DEVICE
        self.diarization_model = None
        logger.info("WhisperX Pipeline Ready.")

    def _load_diarization_model(self):
        """가능한 환경이면 WhisperX diarization 파이프라인을 초기화합니다."""
        if not config.REFERENCE_ENABLE_DIARIZATION:
            return None

        hf_token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
        if not hf_token:
            try:
                from huggingface_hub import get_token

                hf_token = get_token()
            except Exception:
                hf_token = None
        if not hf_token:
            logger.info(
                "HF token 없음 → diarization 비활성화 "
                "(env 또는 huggingface_hub 로그인 필요)"
            )
            return None

        diarization_cls = getattr(whisperx, "DiarizationPipeline", None)
        if diarization_cls is None:
            try:
                diarize_module = importlib.import_module("whisperx.diarize")
                diarization_cls = getattr(
                    diarize_module,
                    "DiarizationPipeline",
                    None,
                )
            except Exception as exc:
                logger.warning("whisperx.diarize import 실패: %s", exc)
                diarization_cls = None
        if diarization_cls is None:
            logger.warning(
                "WhisperX diarization pipeline 을 찾을 수 없습니다."
            )
            return None

        try:
            logger.info("Loading WhisperX diarization pipeline ...")
            try:
                diarization_model = diarization_cls(
                    model_name="pyannote/speaker-diarization-3.1",
                    use_auth_token=hf_token,
                    device=self.diarization_device,
                )
            except TypeError:
                diarization_model = diarization_cls(
                    model_name="pyannote/speaker-diarization-3.1",
                    auth_token=hf_token,
                    device=self.diarization_device,
                )
            if diarization_model is None:
                raise RuntimeError(
                    "Diarization pipeline 초기화 결과가 None 입니다. "
                    "pyannote gated 모델 접근 권한 또는 토큰 설정을 확인하세요."
                )
            return diarization_model
        except Exception as exc:
            logger.warning(
                "Diarization pipeline 로드 실패: %s | "
                "https://hf.co/pyannote/speaker-diarization-3.1 에서 "
                "약관 수락 및 토큰 권한을 확인하세요.",
                exc,
            )
            return None

    def _apply_diarization(
        self, audio: np.ndarray, result: dict
    ) -> tuple[dict, bool]:
        """가능한 경우 정렬 결과에 화자 라벨을 부여합니다."""
        if self.diarization_model is None:
            self.diarization_model = self._load_diarization_model()
        if self.diarization_model is None:
            return result, False

        assign_word_speakers = getattr(whisperx, "assign_word_speakers", None)
        if assign_word_speakers is None:
            try:
                diarize_module = importlib.import_module("whisperx.diarize")
                assign_word_speakers = getattr(
                    diarize_module,
                    "assign_word_speakers",
                    None,
                )
            except Exception as exc:
                logger.warning(
                    "whisperx.diarize.assign_word_speakers import 실패: %s",
                    exc,
                )
                assign_word_speakers = None
        if assign_word_speakers is None:
            logger.warning(
                "WhisperX assign_word_speakers 를 찾을 수 없습니다."
            )
            return result, False

        try:
            diarization_kwargs: dict = {}
            if config.REFERENCE_DIARIZATION_NUM_SPEAKERS is not None:
                diarization_kwargs["num_speakers"] = (
                    config.REFERENCE_DIARIZATION_NUM_SPEAKERS
                )
            else:
                if config.REFERENCE_DIARIZATION_MIN_SPEAKERS is not None:
                    diarization_kwargs["min_speakers"] = (
                        config.REFERENCE_DIARIZATION_MIN_SPEAKERS
                    )
                if config.REFERENCE_DIARIZATION_MAX_SPEAKERS is not None:
                    diarization_kwargs["max_speakers"] = (
                        config.REFERENCE_DIARIZATION_MAX_SPEAKERS
                    )

            diarize_segments = self.diarization_model(
                audio,
                **diarization_kwargs,
            )
            diarized_result = assign_word_speakers(diarize_segments, result)
            return diarized_result, True
        except Exception as exc:
            logger.warning("Diarization 적용 실패: %s", exc)
            return result, False

    # ------------------------------------------------------------------
    # WhisperX STT + Forced Alignment
    # ------------------------------------------------------------------
    def extract_whisper_stats(self, audio_path: str) -> dict:
        """
        WhisperX 를 이용해 텍스트, VAD 통계, 단어별 타임스탬프를 반환합니다.
        """
        audio = whisperx.load_audio(audio_path)
        result = self.stt_model.transcribe(audio, batch_size=config.BATCH_SIZE)

        result = whisperx.align(
            result["segments"],
            self.align_model,
            self.align_metadata,
            audio,
            self.device,
            return_char_alignments=False,
        )
        result, diarization_used = self._apply_diarization(audio, result)

        segments = result["segments"]

        if not segments:
            return _empty_stats(audio)

        text_parts: list[str] = []
        word_timestamps: list[dict] = []

        for seg in segments:
            text_parts.append(seg["text"])
            if "words" in seg:
                for word_info in seg["words"]:
                    if "start" in word_info and "end" in word_info:
                        word_timestamps.append(
                            {
                                "word": word_info["word"].strip(),
                                "start": word_info["start"],
                                "end": word_info["end"],
                                "score": word_info.get("score", 0.0),
                                "speaker": word_info.get("speaker"),
                            }
                        )

        active_speech_sec = _sum_word_durations(word_timestamps)
        if active_speech_sec <= 0:
            active_speech_sec = sum(
                seg["end"] - seg["start"] for seg in segments
            )

        pause_count = count_pauses_from_words(word_timestamps)
        first_speech_start = segments[0]["start"]
        last_speech_end = segments[-1]["end"]

        return {
            "text": " ".join(text_parts).strip(),
            "active_speech_sec": active_speech_sec,
            "pause_count": pause_count,
            "start_time": first_speech_start,
            "end_time": last_speech_end,
            "word_timestamps": word_timestamps,
            "audio_array": audio,
            "diarization_used": diarization_used,
        }

    # ------------------------------------------------------------------
    # Caption-based Fast Path: Align Only (STT transcribe 불필요)
    # ------------------------------------------------------------------
    def align_text_to_audio(
        self,
        audio_path: str,
        caption_text: str,
        confidence_threshold: float = config.GHOST_WORD_CONFIDENCE,
    ) -> dict:
        """
        유튜브 자막(caption_text)과 오디오를 WhisperX forced alignment 만으로 정렬합니다.
        STT transcribe 를 완전히 건너뛰므로 응답 속도가 ~10배 빠릅니다.

        동작 원리 (4단계 Ghost Word 제거)
        ----------------------------------
        1. 패딩된 자막 텍스트를 단일 세그먼트로 포장
        2. WhisperX align 이 텍스트 단어를 오디오 파형에 강제 매핑
        3. 오디오에 존재하지 않는 유령 단어 → timestamp 없음 or score 낮음
        4. confidence_threshold 이하 필터링 → 진짜 단어만 생존

        Parameters
        ----------
        caption_text         : 패딩 포함 자막 원문
        confidence_threshold : 이 점수 미만이면 유령 단어로 간주 (기본 0.5)

        Returns
        -------
        extract_whisper_stats() 와 동일한 구조의 dict
        """
        audio = whisperx.load_audio(audio_path)
        audio_duration = float(
            len(audio) / 16000
        )  # whisperx.load_audio → 16kHz float32

        # 단일 세그먼트로 포장 (WhisperX align 입력 형식)
        segments_input = [
            {
                "text": caption_text.strip(),
                "start": 0.0,
                "end": audio_duration,
            }
        ]

        result = whisperx.align(
            segments_input,
            self.align_model,
            self.align_metadata,
            audio,
            self.device,
            return_char_alignments=False,
        )
        result, diarization_used = self._apply_diarization(audio, result)

        segments = result.get("segments", [])
        if not segments:
            return _empty_stats(
                audio,
                stt_method="caption_align",
            )

        # -------------------------------------------------------
        # 유령 단어 필터링:
        #   ① timestamp 없는 단어 제거 (오디오에서 찾지 못함)
        #   ② score < confidence_threshold 단어 제거 (억지 매칭)
        # -------------------------------------------------------
        valid_words: list[dict] = []
        for seg in segments:
            for w in seg.get("words", []):
                has_ts = "start" in w and "end" in w
                confident = w.get("score", 0.0) >= confidence_threshold
                if has_ts and confident:
                    valid_words.append(
                        {
                            "word": w["word"].strip(),
                            "start": w["start"],
                            "end": w["end"],
                            "score": w["score"],
                            "speaker": w.get("speaker"),
                        }
                    )

        if not valid_words:
            return _empty_stats(
                audio,
                stt_method="caption_align",
                diarization_used=diarization_used,
            )

        # -------------------------------------------------------
        # 진짜 단어들로 stats 재조립
        # -------------------------------------------------------
        final_text = " ".join(w["word"] for w in valid_words)

        active_speech_sec = sum(w["end"] - w["start"] for w in valid_words)

        pause_count = count_pauses_from_words(valid_words)

        logger.info(
            "align_text_to_audio: %d/%d caption words survived (threshold=%.2f)",
            len(valid_words),
            len(caption_text.split()),
            confidence_threshold,
        )
        caption_health = evaluate_caption_alignment_health(
            caption_text,
            valid_words,
            audio_duration,
        )

        return {
            "text": final_text,
            "active_speech_sec": active_speech_sec,
            "pause_count": pause_count,
            "start_time": valid_words[0]["start"],
            "end_time": valid_words[-1]["end"],
            "word_timestamps": valid_words,
            "stt_method": "caption_align",
            "audio_array": audio,
            "diarization_used": diarization_used,
            **caption_health,
        }

    # ------------------------------------------------------------------
    # Prosody Feature Extraction (Pitch + Energy)
    # ------------------------------------------------------------------
    def extract_prosody_features(
        self,
        y: np.ndarray,
        sr: int,
        denoise: bool = False,
        denoise_profile: str | None = None,
        hop_length: int | None = None,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        물리적 억양(F0) 및 에너지(RMS) 특징을 추출하고 정규화합니다.

        Parameters
        ----------
        denoise : bool
            True이면 librosa 분석 전에 Track B 디노이징을 적용합니다.
            WhisperX STT에는 절대 True로 설정하지 마세요 (Two-Track 원칙).

        Returns
        -------
        f0 : 원시 F0 배열 (Hz, 무성음=0)
        rms : 원시 RMS 배열
        features : [T, 2] 정규화된 (f0_norm, rms_norm) 결합 벡터
        """
        y_analysis = (
            denoise_for_analysis(y, sr, profile=denoise_profile)
            if denoise
            else y
        )
        if hop_length is None:
            hop_length = config.HOP_LENGTH
        # 1. Energy (RMS)
        rms = librosa.feature.rms(
            y=y_analysis,
            hop_length=hop_length,
        )[0]

        # 2. Pitch (F0) — y_analysis 로 분석
        f0, _, _ = librosa.pyin(
            y_analysis,
            sr=sr,
            fmin=librosa.note_to_hz("C2"),
            fmax=librosa.note_to_hz("C7"),
            hop_length=hop_length,
        )
        f0 = np.nan_to_num(f0)

        # P1 수정: F0 / RMS 길이 정합
        min_len = min(len(f0), len(rms))
        f0 = f0[:min_len]
        rms = rms[:min_len]

        # RMS Z-score 정규화
        rms_norm = (rms - np.mean(rms)) / (np.std(rms) + 1e-8)

        # F0 화자 정규화 (유성음 구간 기준)
        f0_norm = _normalize_f0(f0)

        features = np.stack([f0_norm, rms_norm], axis=-1)
        return f0, rms, features

    # ------------------------------------------------------------------
    # Boundary Tone Analysis (문장 끝 억양 방향)
    # ------------------------------------------------------------------
    def analyze_boundary_tone(
        self, ref_f0: np.ndarray, user_f0: np.ndarray
    ) -> tuple[float, dict]:
        """
        문장 끝부분(마지막 15%)의 억양 기울기(Slope)를 비교합니다.

        Returns
        -------
        score : 0 ~ 100 점수
        details : {"ref_slope": ..., "user_slope": ..., "status": ...}
        """
        r_valid = ref_f0[ref_f0 > 0]
        u_valid = user_f0[user_f0 > 0]

        # P0 수정: 짧을 때도 dict 반환
        if len(r_valid) < 10 or len(u_valid) < 10:
            return 100.0, {
                "ref_slope": 0.0,
                "user_slope": 0.0,
                "status": "short",
            }

        # 마지막 15% 구간
        r_tail = r_valid[int(len(r_valid) * 0.85) :]
        u_tail = u_valid[int(len(u_valid) * 0.85) :]

        r_x = np.linspace(0, 1, len(r_tail))
        u_x = np.linspace(0, 1, len(u_tail))

        r_tail_st = 12.0 * np.log2(np.maximum(r_tail, 1e-8) / 55.0)
        u_tail_st = 12.0 * np.log2(np.maximum(u_tail, 1e-8) / 55.0)

        r_m, _ = np.polyfit(r_x, r_tail_st, 1)
        u_m, _ = np.polyfit(u_x, u_tail_st, 1)

        SLOPE_THRESHOLD = config.BOUNDARY_SLOPE_THRESHOLD

        if abs(r_m) < SLOPE_THRESHOLD and abs(u_m) < SLOPE_THRESHOLD:
            score = 100.0
            status = "good"
        elif abs(r_m) < SLOPE_THRESHOLD:
            score = max(
                60.0,
                100.0 - (abs(u_m) / (SLOPE_THRESHOLD * 2)) * 40.0,
            )
            status = "weak"
        elif (r_m * u_m) < 0:
            score = 40.0
            status = "opposite"
        else:
            r_mag, u_mag = abs(r_m), abs(u_m)
            max_mag = max(r_mag, u_mag)
            score = (
                100.0
                if max_mag == 0
                else 100.0 * ((min(r_mag, u_mag) / max_mag) ** config.BOUNDARY_K)
            )
            status = "good" if score > config.BOUNDARY_GOOD_THRESHOLD else "weak"

        return round(float(score), 1), {
            "ref_slope": round(float(r_m), 1),
            "user_slope": round(float(u_m), 1),
            "status": status,
        }

    # ------------------------------------------------------------------
    # Dynamic Stress Analysis (볼륨 역동성)
    # ------------------------------------------------------------------
    def analyze_dynamic_stress(
        self, ref_rms: np.ndarray, user_rms: np.ndarray
    ) -> tuple[float, dict]:
        """
        음성 에너지(RMS)의 변동 계수(CV)를 비교하여 역동성 점수를 산출합니다.

        Returns
        -------
        score : 0 ~ 100 점수
        details : {"ref_dynamic_ratio": ..., "user_dynamic_ratio": ..., "status": ...}
        """
        r_mean = np.mean(ref_rms)
        u_mean = np.mean(user_rms)

        if r_mean == 0 or u_mean == 0:
            return 100.0, {
                "ref_dynamic_ratio": 0.0,
                "user_dynamic_ratio": 0.0,
                "status": "flat",
            }

        r_cv = float(np.std(ref_rms) / r_mean)
        u_cv = float(np.std(user_rms) / u_mean)

        max_cv = max(r_cv, u_cv)
        score = (
            100.0
            if max_cv == 0
            else 100.0 * ((min(r_cv, u_cv) / max_cv) ** config.DYNAMIC_K)
        )

        GOOD_SCORE_THRESHOLD = config.DYNAMIC_GOOD_THRESHOLD
        if score >= GOOD_SCORE_THRESHOLD:
            status = "good"
        elif u_cv < r_cv:
            status = "monotone"
        else:
            status = "exaggerated"

        return round(float(score), 1), {
            "ref_dynamic_ratio": round(r_cv, 2),
            "user_dynamic_ratio": round(u_cv, 2),
            "status": status,
        }

    # ------------------------------------------------------------------
    # Word Alignment: 유저 단어를 레퍼런스 구조에 맞게 통일
    # ------------------------------------------------------------------
    @staticmethod
    def _align_user_words_to_ref(
        ref_words: list[dict],
        user_words: list[dict],
    ) -> list[dict]:
        """
        레퍼런스에 "Hollywood casting" 같은 뭉친 단어가 있으면,
        유저의 "Hollywood" + "casting"을 하나로 병합하여
        양쪽 구조를 통일합니다.
        """
        aligned: list[dict] = []
        available = list(user_words)

        def _build_canonical_span(
            words: list[dict],
        ) -> tuple[list[str], dict] | None:
            canonical_tokens: list[str] = []
            matched_words: list[dict] = []

            for word in words:
                tokens = _canonicalize_tokens(word.get("word", ""))
                if not tokens:
                    continue
                canonical_tokens.extend(tokens)
                matched_words.append(word)

            if not canonical_tokens or not matched_words:
                return None

            merged_entry = {
                "word": " ".join(
                    word.get("word", "") for word in matched_words
                ),
                "start": matched_words[0]["start"],
                "end": matched_words[-1]["end"],
                "score": sum(word.get("score", 0.0) for word in matched_words)
                / len(matched_words),
            }
            return canonical_tokens, merged_entry

        for r_word in ref_words:
            ref_tokens = _canonicalize_tokens(r_word.get("word", ""))
            if not ref_tokens:
                continue

            match_found = False
            for start_idx in range(len(available)):
                candidate_words: list[dict] = []
                candidate_tokens: list[str] = []

                for end_idx in range(start_idx, len(available)):
                    candidate_words.append(available[end_idx])
                    canonical_span = _build_canonical_span(candidate_words)
                    if canonical_span is None:
                        continue

                    candidate_tokens, merged_entry = canonical_span
                    if len(candidate_tokens) > len(ref_tokens):
                        break
                    if candidate_tokens == ref_tokens:
                        indices = list(range(start_idx, end_idx + 1))
                        aligned.append(merged_entry)
                        for idx in sorted(indices, reverse=True):
                            available.pop(idx)
                        match_found = True
                        break

                if match_found:
                    break

        aligned.extend(available)
        return aligned

    # ------------------------------------------------------------------
    # Word Rhythm Analysis (단어별 미시 리듬)
    # ------------------------------------------------------------------
    def analyze_word_rhythm(
        self,
        ref_words: list[dict],
        user_words: list[dict],
        ref_active_time: float,
        user_active_time: float,
    ) -> tuple[float, list[dict]]:
        """단어별 상대적 길이(RD)를 비교하여 리듬 점수를 산출합니다."""
        if (
            not ref_words
            or not user_words
            or ref_active_time <= 0
            or user_active_time <= 0
        ):
            return 100.0, []

        word_scores: list[float] = []
        word_feedback: list[dict] = []

        # dict 기반 O(n) 매칭용 인덱스 구축
        _user_word_index: dict[str, list[int]] = {}
        for _ui, _uw in enumerate(user_words):
            _key = _normalize_word(_uw["word"])
            if _key:
                _user_word_index.setdefault(_key, []).append(_ui)
        _used_indices: set[int] = set()

        k = config.RHYTHM_K

        for r_word in ref_words:
            r_text = _normalize_word(r_word["word"])
            if not r_text:
                continue

            matched_idx = -1
            candidates = _user_word_index.get(r_text, [])
            for ci in candidates:
                if ci not in _used_indices:
                    matched_idx = ci
                    break

            if matched_idx != -1:
                _used_indices.add(matched_idx)
                u_word = user_words[matched_idx]

                r_dur = r_word["end"] - r_word["start"]
                u_dur = u_word["end"] - u_word["start"]

                r_rd = r_dur / ref_active_time
                u_rd = u_dur / user_active_time

                max_rd = max(r_rd, u_rd)
                word_score = (
                    1.0 if max_rd == 0 else (min(r_rd, u_rd) / max_rd) ** k
                )

                word_scores.append(word_score)

                diff_ratio = abs(r_rd - u_rd) / ((r_rd + u_rd) / 2 + 1e-8)
                status = "good"
                if diff_ratio > config.RHYTHM_DIFF_THRESHOLD:
                    status = "dragged" if u_rd > r_rd else "rushed"

                word_feedback.append(
                    {
                        "word": r_word["word"],
                        "status": status,
                        "ref_start_time": round(r_word["start"], 2),
                        "ref_end_time": round(r_word["end"], 2),
                        "ref_duration_sec": round(r_dur, 2),
                        "user_start_time": round(u_word["start"], 2),
                        "user_end_time": round(u_word["end"], 2),
                        "user_duration_sec": round(u_dur, 2),
                    }
                )
            else:
                word_feedback.append(
                    {
                        "word": r_word["word"],
                        "status": "missed",
                        "ref_start_time": round(r_word["start"], 2),
                        "ref_end_time": round(r_word["end"], 2),
                        "user_start_time": None,
                        "user_end_time": None,
                    }
                )

        rhythm_score = (
            100.0 * (sum(word_scores) / len(word_scores))
            if word_scores
            else 100.0
        )
        return round(rhythm_score, 1), word_feedback

    # ------------------------------------------------------------------
    # Prosody Similarity (DTW)
    # ------------------------------------------------------------------
    def analyze_prosody(
        self,
        ref_features: np.ndarray,
        user_features: np.ndarray,
    ) -> float:
        """
        정규화된 억양+강세 특징 벡터를 DTW 로 비교하여 유사도 점수를 반환합니다.
        """
        distance, path = fastdtw(
            ref_features,
            user_features,
            dist=euclidean,
            radius=config.PROSODY_DTW_RADIUS,
        )
        normalized_distance = distance / len(path)

        prosody_score = 100.0 * np.exp(
            -config.PROSODY_BETA * normalized_distance
        )

        return round(float(prosody_score), 1)

    # ------------------------------------------------------------------
    # Word-level Pitch Contour Feedback (단어별 F0 높낮이 비교)
    # ------------------------------------------------------------------
    def analyze_word_pitch_contour(
        self,
        ref_f0: np.ndarray,
        user_f0: np.ndarray,
        ref_words: list[dict],
        user_words: list[dict],
        sr: int = 16000,
        hop_length: int = 256,
        ref_speech_start: float = 0.0,
        user_speech_start: float = 0.0,
    ) -> list[dict]:
        """
        각 단어 구간의 F0 시작/종료 방향을 비교하여 피치 피드백을 생성합니다.
        """

        def _direction(f0_arr, start_sec, end_sec, speech_start):
            rel_start = max(0.0, start_sec - speech_start)
            rel_end = max(0.0, end_sec - speech_start)

            s_frame = int(rel_start * sr / hop_length)
            e_frame = int(rel_end * sr / hop_length)
            if s_frame >= len(f0_arr) or e_frame <= s_frame:
                return "flat", 0.0, 0.0
            segment = f0_arr[s_frame:e_frame]
            valid = segment[segment > 0]

            if len(valid) == 0:
                return "flat", 0.0, 0.0
            elif len(valid) == 1:
                val = float(valid[0])
                return "flat", val, val
            elif len(valid) < 4:
                first_half = float(valid[0])
                second_half = float(valid[-1])
                diff = second_half - first_half
                if abs(diff) < config.PITCH_FLAT_THRESHOLD_HZ:
                    return "flat", first_half, second_half
                return (
                    ("rising" if diff > 0 else "falling"),
                    first_half,
                    second_half,
                )

            mid = len(valid) // 2
            first_half = float(np.mean(valid[:mid]))
            second_half = float(np.mean(valid[mid:]))
            diff = second_half - first_half
            if abs(diff) < config.PITCH_FLAT_THRESHOLD_HZ:
                return "flat", first_half, second_half
            return (
                ("rising" if diff > 0 else "falling"),
                first_half,
                second_half,
            )

        pitch_feedback: list[dict] = []

        # dict 기반 O(n) 매칭용 인덱스 구축
        _pitch_user_index: dict[str, list[int]] = {}
        for _pi, _pw in enumerate(user_words):
            _pkey = _normalize_word(_pw.get("word", ""))
            if _pkey:
                _pitch_user_index.setdefault(_pkey, []).append(_pi)
        _pitch_used: set[int] = set()

        for r_word in ref_words:
            r_text_clean = _normalize_word(r_word.get("word", ""))
            if not r_text_clean:
                continue

            matched_u = None
            for _ci in _pitch_user_index.get(r_text_clean, []):
                if _ci not in _pitch_used:
                    _pitch_used.add(_ci)
                    matched_u = user_words[_ci]
                    break

            if matched_u is None:
                continue

            r_dir, r_first, r_second = _direction(
                ref_f0,
                r_word["start"],
                r_word["end"],
                ref_speech_start,
            )
            u_dir, u_first, u_second = _direction(
                user_f0,
                matched_u["start"],
                matched_u["end"],
                user_speech_start,
            )

            feedback = "good"
            if r_dir != u_dir and r_dir != "flat":
                if r_dir == "rising" and u_dir != "rising":
                    feedback = "raise_end"
                elif r_dir == "falling" and u_dir != "falling":
                    feedback = "lower_end"
            elif r_dir == u_dir and r_dir != "flat":
                r_delta = abs(r_second - r_first)
                u_delta = abs(u_second - u_first)
                if r_delta > 0 and u_delta / (r_delta + 1e-8) < 0.5:
                    feedback = "more_emphasis"

            pitch_feedback.append(
                {
                    "word": r_word["word"],
                    "ref_direction": r_dir,
                    "ref_start_hz": round(r_first, 1),
                    "ref_end_hz": round(r_second, 1),
                    "user_direction": u_dir,
                    "user_start_hz": round(u_first, 1),
                    "user_end_hz": round(u_second, 1),
                    "feedback": feedback,
                }
            )

        return pitch_feedback

    # ------------------------------------------------------------------
    # 위치 기반 멈춤 정합 분석 (Alignment-Based Pause Scoring)
    # ------------------------------------------------------------------
    def analyze_pause_alignment(
        self,
        ref_words: list[dict],
        aligned_user_words: list[dict],
    ) -> tuple[float, dict]:
        """
        레퍼런스와 유저의 휴지기(Pause) 위치를 1:1로 비교합니다.

        Returns
        -------
        f1_score : 0.0 ~ 1.0 사이의 F1 점수
        details  : {true_hits, false_alarms, misses, precision, recall}
        """
        ref_pauses = extract_pause_positions(ref_words)
        user_pauses = extract_pause_positions(aligned_user_words)

        # 레퍼런스에 쉼이 아예 없는 경우
        if not ref_pauses:
            # 유저도 안 쉬면 만점, 쉬면 감점
            if not user_pauses:
                return 1.0, {
                    "true_hits": 0,
                    "false_alarms": 0,
                    "misses": 0,
                    "precision": 1.0,
                    "recall": 1.0,
                }
            return 0.0, {
                "true_hits": 0,
                "false_alarms": len(user_pauses),
                "misses": 0,
                "precision": 0.0,
                "recall": 1.0,
            }

        true_hits = len(ref_pauses & user_pauses)
        false_alarms = len(user_pauses - ref_pauses)
        misses = len(ref_pauses - user_pauses)

        precision = (
            true_hits / (true_hits + false_alarms)
            if (true_hits + false_alarms) > 0
            else 0.0
        )
        recall = (
            true_hits / (true_hits + misses)
            if (true_hits + misses) > 0
            else 0.0
        )
        f1 = (
            2 * precision * recall / (precision + recall)
            if (precision + recall) > 0
            else 0.0
        )

        return f1, {
            "true_hits": true_hits,
            "false_alarms": false_alarms,
            "misses": misses,
            "precision": round(precision, 3),
            "recall": round(recall, 3),
        }

    # ------------------------------------------------------------------
    # 종합 평가 (Evaluate)
    # ------------------------------------------------------------------
    def evaluate(self, user_audio_path: str, ref_data: dict) -> dict:
        """
        유저 오디오와 레퍼런스 JSON 데이터를 비교하여 7대 지표 JSON 을 반환합니다.
        """
        import tempfile

        from domain.processing.audio_processing import peak_normalize_audio

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
        from scipy.io import wavfile

        wavfile.write(
            norm_tmp_path,
            target_sr,
            np.asarray(user_y_normalized, dtype=np.float32),
        )

        # 1. 유저 STT (Peak 정규화된 오디오 사용 → 인식률 향상)
        try:
            user_stats = self.extract_whisper_stats(norm_tmp_path)
        finally:
            import os

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
        aligned_user_words = self._align_user_words_to_ref(
            ref_word_timestamps,
            user_stats["word_timestamps"],
        )

        # 3. 단어 정확도 (WER → 지수 감쇠 스코어링)
        wer = jiwer.wer(
            _REMOVE_PUNCT(ref_text.lower()),
            _REMOVE_PUNCT(user_text.lower()),
        )
        word_score = 100.0 * np.exp(-config.WER_PENALTY * wer)

        # 4. 속도 점수
        ref_active_time = _sum_word_durations(ref_word_timestamps)
        user_active_time = user_stats["active_speech_sec"]

        k = config.SPEED_K
        rushing_penalty = config.SPEED_RUSHING_PENALTY
        if ref_active_time <= 0 and user_active_time <= 0:
            speed_score = 100.0
        elif ref_active_time <= 0 or user_active_time <= 0:
            speed_score = 0.0
        else:
            speed_ratio = user_active_time / ref_active_time
            if speed_ratio < 1.0:
                speed_score = 100.0 * (speed_ratio ** (k * rushing_penalty))
            else:
                speed_score = 100.0 * ((1.0 / speed_ratio) ** k)

        # 5. 멈춤 점수 (횟수 기반 + 위치 정합 F1 블렌딩)
        ref_pause_count = count_pauses_from_words(ref_word_timestamps)
        user_pause_count = user_stats["pause_count"]
        pause_diff = abs(user_pause_count - ref_pause_count)
        count_score = 100.0 * np.exp(
            -((pause_diff**2) / (2 * (config.PAUSE_SIGMA**2)))
        )

        # 위치 정합 F1 점수
        f1, pause_align_details = self.analyze_pause_alignment(
            ref_word_timestamps,
            aligned_user_words,
        )
        align_score = 100.0 * f1

        w = config.PAUSE_ALIGN_WEIGHT
        pause_score = (1.0 - w) * count_score + w * align_score

        # 6. 단어별 리듬 (통일된 유저 단어 사용)
        rhythm_score, word_feedback = self.analyze_word_rhythm(
            ref_word_timestamps,
            aligned_user_words,
            ref_active_time,
            user_active_time,
        )

        # 7. 유저 오디오 피처 추출 (원본 오디오 사용, 내부 Z-score가 볼륨 처리)
        start_idx = int(user_stats["start_time"] * target_sr)
        end_idx = int(user_stats["end_time"] * target_sr)
        user_y_cropped = (
            user_y_raw[start_idx:end_idx]
            if end_idx > start_idx
            else user_y_raw
        )

        user_f0, user_rms, user_features = self.extract_prosody_features(
            user_y_cropped, target_sr, denoise=True, hop_length=hop_length
        )

        # 8. 억양 DTW + 세부 분석 (레퍼런스 피처 복원)
        if len(ref_f0) > 0 and len(ref_rms) > 0:
            min_len = min(len(ref_f0), len(ref_rms))
            ref_f0_t = ref_f0[:min_len]
            ref_rms_t = ref_rms[:min_len]

            ref_f0_norm = _normalize_f0(ref_f0_t)

            ref_rms_norm = (ref_rms_t - np.mean(ref_rms_t)) / (
                np.std(ref_rms_t) + 1e-8
            )
            ref_features = np.stack([ref_f0_norm, ref_rms_norm], axis=-1)

            prosody_score = self.analyze_prosody(ref_features, user_features)
            boundary_score, boundary_details = self.analyze_boundary_tone(
                ref_f0_t, user_f0
            )
            dynamic_score, dynamic_details = self.analyze_dynamic_stress(
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
            pitch_contour = self.analyze_word_pitch_contour(
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
