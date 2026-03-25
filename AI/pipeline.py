"""StyleEcho Pipeline.

WhisperX 기반 STT, Forced Alignment, Diarization, Prosody 분석,
종합 채점(evaluate) 등 모델 의존 로직을 담당합니다.
"""

import logging
import threading
from typing import Any

import numpy as np
import torch

# Pyannote.audio 호환성 패치: torchaudio >= 2.1 에서 AudioMetaData 삭제 대응
import torchaudio

if not hasattr(torchaudio, "AudioMetaData"):
    torchaudio.AudioMetaData = type("AudioMetaData", (), {})

import config
import whisperx
from domain.processing.engine_utils import (
    _sum_word_durations,
    count_pauses_from_words,
)
from domain.processing.quality import evaluate_caption_alignment_health
from domain.prosody.feature_extraction import (
    extract_prosody_features as _extract_prosody_features,
)
from domain.scoring import aggregator as scoring_aggregator
from domain.scoring.boundary_scoring import (
    analyze_boundary_tone as _analyze_boundary_tone,
)
from domain.scoring.dynamic_scoring import (
    analyze_dynamic_stress as _analyze_dynamic_stress,
)
from domain.scoring.pause_scoring import (
    analyze_pause_alignment as _analyze_pause_alignment,
)
from domain.scoring.pitch_contour import (
    analyze_word_pitch_contour as _analyze_word_pitch_contour,
)
from domain.scoring.prosody_scoring import analyze_prosody as _analyze_prosody
from domain.scoring.rhythm_scoring import (
    analyze_word_rhythm as _analyze_word_rhythm,
)
from domain.scoring.word_alignment import (
    align_user_words_to_ref as _align_user_words_to_ref,
)
from domain.stt.diarization import apply_diarization as _apply_diarization
from domain.stt.diarization import (
    load_diarization_model as _load_diarization_model,
)

logger = logging.getLogger(__name__)


# ── 하위 호환 re-export (reference_service.py 등에서 import) ──
from domain.prosody.source_selection import (  # noqa: F401
    select_reference_prosody_sources,
)


def _empty_stats(
    audio: np.ndarray | None = None,
    *,
    stt_method: str | None = None,
    diarization_used: bool = False,
) -> dict[str, Any]:
    """빈 STT 결과 페이로드를 생성합니다.

    Args:
        audio: 결과에 연결된 오디오 배열 (선택).
        stt_method: STT 방식 라벨 (선택).
        diarization_used: diarization 적용 여부.

    Returns:
        빈 STT 통계 페이로드.
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
# 싱글턴 관리
# ---------------------------------------------------------------------------
_pipeline_instance: "StyleEchoPipeline | None" = None
_pipeline_lock = threading.Lock()


def get_pipeline(
    whisper_model_size: str = "large-v3",
    device: str | None = None,
    compute_type: str = "float16",
) -> "StyleEchoPipeline":
    """싱글턴 ``StyleEchoPipeline`` 인스턴스를 반환합니다.

    Args:
        whisper_model_size: 최초 초기화 시 로드할 Whisper 모델 크기.
        device: 명시적 디바이스 지정. ``None`` 이면 자동 추론.
        compute_type: Whisper 연산 타입.

    Returns:
        공유 ``StyleEchoPipeline`` 인스턴스.
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
# 코어 파이프라인
# ---------------------------------------------------------------------------
class StyleEchoPipeline:
    """WhisperX 기반 영어 억양·발음 평가 파이프라인."""

    def __init__(
        self,
        whisper_model_size: str = "base",
        device: str = "cuda",
        compute_type: str = "float16",
    ) -> None:
        """WhisperX 모델을 로드하고 파이프라인을 초기화합니다.

        Args:
            whisper_model_size: Whisper 모델 크기.
            device: 실행 디바이스 (``cuda`` 또는 ``cpu``).
            compute_type: Whisper 연산 타입.
        """
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

    def _load_diarization_model(self) -> Any | None:
        """``domain.stt.diarization`` 에 위임합니다."""
        return _load_diarization_model(self.diarization_device)

    def _apply_diarization(
        self, audio: np.ndarray, result: dict[str, Any]
    ) -> tuple[dict[str, Any], bool]:
        """``domain.stt.diarization`` 에 위임합니다."""
        if self.diarization_model is None:
            self.diarization_model = self._load_diarization_model()
        return _apply_diarization(audio, result, self.diarization_model)

    # ------------------------------------------------------------------
    # WhisperX STT + 강제 정렬
    # ------------------------------------------------------------------
    def extract_whisper_stats(self, audio_path: str) -> dict[str, Any]:
        """WhisperX 를 이용해 텍스트, VAD 통계, 단어별 타임스탬프를 반환합니다.

        Args:
            audio_path: 입력 오디오 파일 경로.

        Returns:
            STT 결과, 활성 발화 시간, pause 수, 단어 타임스탬프 등을 포함한 딕셔너리.
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
    # 자막 기반 빠른 경로: 정렬만 수행 (STT transcribe 불필요)
    # ------------------------------------------------------------------
    def align_text_to_audio(
        self,
        audio_path: str,
        caption_text: str,
        confidence_threshold: float = config.GHOST_WORD_CONFIDENCE,
    ) -> dict[str, Any]:
        """유튜브 자막과 오디오를 WhisperX forced alignment 만으로 정렬합니다.

        STT transcribe 를 완전히 건너뛰므로 응답 속도가 ~10배 빠릅니다.

        동작 원리 (4단계 Ghost Word 제거):

        1. 패딩된 자막 텍스트를 단일 세그먼트로 포장
        2. WhisperX align 이 텍스트 단어를 오디오 파형에 강제 매핑
        3. 오디오에 존재하지 않는 유령 단어 → timestamp 없음 or score 낮음
        4. confidence_threshold 이하 필터링 → 진짜 단어만 생존

        Args:
            audio_path: 입력 오디오 파일 경로.
            caption_text: 패딩 포함 자막 원문.
            confidence_threshold: 이 점수 미만이면 유령 단어로 간주합니다.

        Returns:
            ``extract_whisper_stats()`` 와 동일한 구조의 딕셔너리.
        """
        audio = whisperx.load_audio(audio_path)
        audio_duration = float(
            len(audio) / 16000
        )  # whisperx.load_audio → 16kHz float32 고정

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
    # 억양 특징 추출 (피치 + 에너지)
    # ------------------------------------------------------------------
    def extract_prosody_features(
        self,
        y: np.ndarray,
        sr: int,
        denoise: bool = False,
        denoise_profile: str | None = None,
        hop_length: int | None = None,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """``domain.prosody.feature_extraction`` 에 위임합니다."""
        return _extract_prosody_features(
            y, sr, denoise, denoise_profile, hop_length
        )

    # ------------------------------------------------------------------
    # 종결 억양 분석 (문장 끝 억양 방향)
    # ------------------------------------------------------------------
    def analyze_boundary_tone(
        self, ref_f0: np.ndarray, user_f0: np.ndarray
    ) -> tuple[float, dict[str, Any]]:
        """``domain.scoring.boundary_scoring`` 에 위임합니다."""
        return _analyze_boundary_tone(ref_f0, user_f0)

    # ------------------------------------------------------------------
    # 동적 강세 분석 (볼륨 역동성)
    # ------------------------------------------------------------------
    def analyze_dynamic_stress(
        self, ref_rms: np.ndarray, user_rms: np.ndarray
    ) -> tuple[float, dict[str, Any]]:
        """``domain.scoring.dynamic_scoring`` 에 위임합니다."""
        return _analyze_dynamic_stress(ref_rms, user_rms)

    # ------------------------------------------------------------------
    # 단어 정렬: 유저 단어를 레퍼런스 구조에 맞게 통일
    # ------------------------------------------------------------------
    @staticmethod
    def _align_user_words_to_ref(
        ref_words: list[dict[str, Any]],
        user_words: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        """``domain.scoring.word_alignment`` 에 위임합니다."""
        return _align_user_words_to_ref(ref_words, user_words)

    # ------------------------------------------------------------------
    # 단어 리듬 분석 (단어별 미시 리듬)
    # ------------------------------------------------------------------
    def analyze_word_rhythm(
        self,
        ref_words: list[dict[str, Any]],
        user_words: list[dict[str, Any]],
        ref_active_time: float,
        user_active_time: float,
    ) -> tuple[float, list[dict[str, Any]]]:
        """``domain.scoring.rhythm_scoring`` 에 위임합니다."""
        return _analyze_word_rhythm(
            ref_words, user_words, ref_active_time, user_active_time
        )

    # ------------------------------------------------------------------
    # 억양 유사도 (DTW)
    # ------------------------------------------------------------------
    def analyze_prosody(
        self,
        ref_features: np.ndarray,
        user_features: np.ndarray,
    ) -> float:
        """``domain.scoring.prosody_scoring`` 에 위임합니다."""
        return _analyze_prosody(ref_features, user_features)

    # ------------------------------------------------------------------
    # 단어별 피치 컨투어 피드백 (단어별 F0 높낮이 비교)
    # ------------------------------------------------------------------
    def analyze_word_pitch_contour(
        self,
        ref_f0: np.ndarray,
        user_f0: np.ndarray,
        ref_words: list[dict[str, Any]],
        user_words: list[dict[str, Any]],
        sr: int = 16000,
        hop_length: int = 256,
        ref_speech_start: float = 0.0,
        user_speech_start: float = 0.0,
    ) -> list[dict[str, Any]]:
        """``domain.scoring.pitch_contour`` 에 위임합니다."""
        return _analyze_word_pitch_contour(
            ref_f0,
            user_f0,
            ref_words,
            user_words,
            sr=sr,
            hop_length=hop_length,
            ref_speech_start=ref_speech_start,
            user_speech_start=user_speech_start,
        )

    # ------------------------------------------------------------------
    # 위치 기반 멈춤 정합 분석 (Alignment-Based Pause Scoring)
    # ------------------------------------------------------------------
    def analyze_pause_alignment(
        self,
        ref_words: list[dict[str, Any]],
        aligned_user_words: list[dict[str, Any]],
    ) -> tuple[float, dict[str, Any]]:
        """``domain.scoring.pause_scoring`` 에 위임합니다."""
        return _analyze_pause_alignment(ref_words, aligned_user_words)

    # ------------------------------------------------------------------
    # 종합 평가 (Evaluate)
    # ------------------------------------------------------------------
    def evaluate(
        self, user_audio_path: str, ref_data: dict[str, Any]
    ) -> dict[str, Any]:
        """``domain.scoring.aggregator`` 에 위임합니다.

        Args:
            user_audio_path: 유저 오디오 파일 경로.
            ref_data: 레퍼런스 JSON 데이터.

        Returns:
            평가 결과 딕셔너리 (status, scores, details 등).
        """
        return scoring_aggregator.evaluate(
            user_audio_path=user_audio_path,
            ref_data=ref_data,
            stt_fn=self.extract_whisper_stats,
            prosody_fn=self.extract_prosody_features,
        )
