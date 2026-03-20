"""
StyleEcho 설정 로더 (OmegaConf 적용 버전)
====================
config_default.yaml 을 기본으로 로드하고,
config.yaml 이 존재하면 해당 값으로 덮어씁니다.
"""

import logging
from pathlib import Path
from typing import Any

from dotenv import load_dotenv  # type: ignore[import-not-found]
from omegaconf import DictConfig, ListConfig, OmegaConf

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# 경로 결정 및 환경변수 로드
# ---------------------------------------------------------------------------
_PIPE_DIR = Path(__file__).resolve().parent
_DEFAULT_PATH = _PIPE_DIR / "config_default.yaml"
_USER_PATH = _PIPE_DIR / "config.yaml"

load_dotenv(_PIPE_DIR / ".env")


# ---------------------------------------------------------------------------
# 로드 및 자동 딥 머지 (Deep Merge)
# ---------------------------------------------------------------------------
def _ensure_dict_config(config_obj: DictConfig | ListConfig) -> DictConfig:
    """루트 설정 객체가 매핑 형태인지 검증합니다."""
    if isinstance(config_obj, DictConfig):
        return config_obj

    raise TypeError("루트 설정은 mapping 형태여야 합니다.")


def _load_config() -> DictConfig:
    """config_default.yaml 과 config.yaml 을 로드하여 병합합니다."""
    if not _DEFAULT_PATH.exists():
        raise FileNotFoundError(f"기본 설정 파일이 없습니다: {_DEFAULT_PATH}")

    base = _ensure_dict_config(OmegaConf.load(_DEFAULT_PATH))

    if _USER_PATH.exists():
        logger.info("사용자 설정 로드: %s", _USER_PATH)
        user = _ensure_dict_config(OmegaConf.load(_USER_PATH))
        return _ensure_dict_config(OmegaConf.merge(base, user))

    logger.info("사용자 설정 없음 → 기본값 사용: %s", _DEFAULT_PATH)
    return base


# ---------------------------------------------------------------------------
# 전역 설정 객체 및 유틸 함수
# ---------------------------------------------------------------------------
cfg: DictConfig = _load_config()


def get(dotted_key: str, default: Any = None) -> Any:
    """
    기존 코드와의 하위 호환성을 위한 get 래퍼 함수.

    예:
        get("whisper.model") -> cfg.whisper.model
    """
    return OmegaConf.select(cfg, dotted_key, default=default)


# ---------------------------------------------------------------------------
# 하위 호환 상수
# ---------------------------------------------------------------------------
WHISPER_MODEL: str = get("whisper.model", "large-v3")
DEVICE: str = get("whisper.device", "auto")
COMPUTE_TYPE: str = get("whisper.compute_type", "float16")
BATCH_SIZE: int = get("whisper.batch_size", 16)

HOST: str = get("server.host", "0.0.0.0")
PORT: int = get("server.port", 8000)

LLM_GEMINI_MODEL: str = get("llm.gemini_model", "gemini-2.0-flash")
LLM_GEMINI_BASE_URL: str = get(
    "llm.gemini_base_url",
    "https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com",
)
LLM_GEMINI_SYSTEM_INSTRUCTION: str = get(
    "llm.gemini_system_instruction",
    "You are an English learning reference processor. Return strict JSON only. Translate into natural and fluent Korean, and prefer natural Korean phrasing over literal word-for-word translation.",
)
LLM_GEMINI_TIMEOUT_SEC: int = get("llm.gemini_timeout_sec", 30)
LLM_GEMINI_MAX_RETRIES: int = get("llm.gemini_max_retries", 3)
LLM_GEMINI_RETRY_BACKOFF_SEC: float = get("llm.gemini_retry_backoff_sec", 1.0)
LLM_GEMINI_TEMPERATURE: float = get("llm.gemini_temperature", 0.2)
LLM_GEMINI_MAX_OUTPUT_TOKENS: int = get("llm.gemini_max_output_tokens", 4096)

TARGET_SR: int = get("audio.target_sr", 16000)
HOP_LENGTH: int = get("audio.hop_length", 256)

AUDIO_CACHE_ENABLED: bool = get("audio.cache.enabled", True)
AUDIO_CACHE_DIR: str = get("audio.cache.dir", "temp/audio_cache")
AUDIO_CACHE_MAX_SIZE_MB: int = get("audio.cache.max_size_mb", 500)
AUDIO_CACHE_TTL_HOURS: int = get("audio.cache.ttl_hours", 24)

DENOISE_ENABLED: bool = get("denoise.enabled", True)
DENOISE_STATIONARY: bool = get("denoise.stationary", True)
DENOISE_PROP_DECREASE: float = get("denoise.prop_decrease", 0.6)
REFERENCE_DENOISE_MODE: str = get("reference.denoise_mode", "auto")
REFERENCE_ENABLE_DIARIZATION: bool = get("reference.enable_diarization", True)
REFERENCE_DIARIZATION_DEVICE: str = get("reference.diarization_device", "cpu")
REFERENCE_DIARIZATION_NUM_SPEAKERS: int | None = get(
    "reference.diarization_num_speakers", None
)
REFERENCE_DIARIZATION_MIN_SPEAKERS: int | None = get(
    "reference.diarization_min_speakers", 1
)
REFERENCE_DIARIZATION_MAX_SPEAKERS: int | None = get(
    "reference.diarization_max_speakers", 3
)
REFERENCE_ALLOW_RISKY: bool = get("reference.allow_risky", True)
REFERENCE_REJECT_ON_HIGH_OVERLAP: bool = get(
    "reference.reject_on_high_overlap", True
)
REFERENCE_REJECT_ON_LOW_ALIGNMENT: bool = get(
    "reference.reject_on_low_alignment", True
)
REFERENCE_MIN_PART_DURATION_SEC: float = get(
    "reference.min_part_duration_sec", 1.0
)
REFERENCE_MAX_PART_MERGE_GAP_SEC: float = get(
    "reference.max_part_merge_gap_sec", 0.35
)
REFERENCE_SHORT_PART_FRAGMENT_MAX_WORDS: int = get(
    "reference.short_part_fragment_max_words", 2
)
REFERENCE_SHORT_PART_KEEP_MIN_WORDS: int = get(
    "reference.short_part_keep_min_words", 3
)
REFERENCE_SHORT_PART_TERMINAL_PROTECTION_ENABLED: bool = get(
    "reference.short_part_terminal_protection_enabled", True
)
REFERENCE_SHORT_PART_TERMINAL_KEEP_MIN_WORDS: int = get(
    "reference.short_part_terminal_keep_min_words", 2
)
REFERENCE_SHORT_PART_TERMINAL_KEEP_MIN_DURATION_SEC: float = get(
    "reference.short_part_terminal_keep_min_duration_sec", 0.45
)
REFERENCE_SHORT_PART_MERGE_MAX_WPM: float = get(
    "reference.short_part_merge_max_wpm", 80.0
)
REFERENCE_TURN_GAP_SEC: float = get("reference.turn_gap_sec", 0.85)
REFERENCE_DIALOG_TURN_MAX_WORDS: int = get(
    "reference.dialog_turn_max_words", 16
)
REFERENCE_SPEAKER_CHANGE_MIN_GAP_SEC: float = get(
    "reference.speaker_change_min_gap_sec", 0.08
)
REFERENCE_SPEAKER_TURN_MIN_WORDS: int = get(
    "reference.speaker_turn_min_words", 2
)
REFERENCE_SPEAKER_TURN_MIN_DURATION_SEC: float = get(
    "reference.speaker_turn_min_duration_sec", 0.45
)
REFERENCE_SPEAKER_LABEL_SMOOTHING_MAX_WORDS: int = get(
    "reference.speaker_label_smoothing_max_words", 2
)
REFERENCE_SPEAKER_LABEL_SMOOTHING_MAX_DURATION_SEC: float = get(
    "reference.speaker_label_smoothing_max_duration_sec", 0.6
)
REFERENCE_MEDIUM_NOISE_SNR_DB: float = get(
    "reference.medium_noise_snr_db", 13.0
)
REFERENCE_HIGH_NOISE_SNR_DB: float = get("reference.high_noise_snr_db", 7.0)
REFERENCE_MEDIUM_SPEECH_RATIO: float = get(
    "reference.medium_speech_ratio", 0.40
)
REFERENCE_LOW_SPEECH_RATIO: float = get("reference.low_speech_ratio", 0.25)
REFERENCE_LOW_ALIGNMENT_SCORE: float = get(
    "reference.low_alignment_score", 0.40
)
REFERENCE_LOW_ALIGNMENT_RATIO: float = get(
    "reference.low_alignment_ratio", 0.60
)
REFERENCE_HIGH_OVERLAP_RATIO: float = get("reference.high_overlap_ratio", 0.42)
REFERENCE_MEDIUM_OVERLAP_RATIO: float = get(
    "reference.medium_overlap_ratio", 0.26
)
REFERENCE_SPEAKER_SHIFT_SEMITONES: float = get(
    "reference.speaker_shift_semitones", 7.0
)
REFERENCE_SINGLE_SPEAKER_PART_DOMINANCE_RATIO: float = get(
    "reference.single_speaker_part_dominance_ratio", 0.7
)
REFERENCE_SINGLE_SPEAKER_DOMINANT_RATIO: float = get(
    "reference.single_speaker_dominant_ratio", 0.82
)
REFERENCE_SINGLE_SPEAKER_MAX_SECOND_RATIO: float = get(
    "reference.single_speaker_max_second_ratio", 0.18
)
REFERENCE_SINGLE_SPEAKER_MAX_LABEL_CHANGE_RATIO: float = get(
    "reference.single_speaker_max_label_change_ratio", 0.45
)
REFERENCE_SINGLE_SPEAKER_MAX_MULTI_PART_RATIO: float = get(
    "reference.single_speaker_max_multi_part_ratio", 0.35
)
REFERENCE_SINGLE_SPEAKER_MIN_PART_RATIO: float = get(
    "reference.single_speaker_min_part_ratio", 0.7
)
REFERENCE_MULTI_SPEAKER_MIN_SECOND_RATIO: float = get(
    "reference.multi_speaker_min_second_ratio", 0.22
)
REFERENCE_MULTI_SPEAKER_MIN_LABEL_CHANGE_RATIO: float = get(
    "reference.multi_speaker_min_label_change_ratio", 0.2
)
REFERENCE_MULTI_SPEAKER_MIN_PART_RATIO: float = get(
    "reference.multi_speaker_min_part_ratio", 0.25
)

AUDIO_PADDING_SEC: float = get("padding.audio_sec", 1.0)
CAPTION_MIN_ENTRY_OVERLAP_RATIO: float = get(
    "padding.caption_min_entry_overlap_ratio", 0.5
)

TRIM_FRONT_SCORE: float = get("trimming.front_score_threshold", 0.6)
TRIM_BACK_SCORE: float = get("trimming.back_score_threshold", 0.45)
TRIM_BOUNDARY_GAP: float = get("trimming.boundary_gap_sec", 0.2)
TRIM_MIN_WORDS: int = get("trimming.min_words", 2)

SPEED_K: float = get("scoring.speed_k", 1.2)
SPEED_RUSHING_PENALTY: float = get("scoring.speed_rushing_penalty", 1.3)
PAUSE_SIGMA: float = get("scoring.pause_sigma", 2.5)
PAUSE_GAP_SEC: float = get("scoring.pause_gap_sec", 0.3)
PAUSE_ALIGN_WEIGHT: float = get("scoring.pause_align_weight", 0.7)
PAUSE_ALIGN_GAP_SEC: float = get("scoring.pause_align_gap_sec", 0.3)
PROSODY_BETA: float = get("scoring.prosody_beta", 1.2)
PROSODY_DTW_RADIUS: int = get("scoring.prosody_dtw_radius", 10)
RHYTHM_K: float = get("scoring.rhythm_k", 1.2)
RHYTHM_DIFF_THRESHOLD: float = get("scoring.rhythm_diff_threshold", 0.4)
BOUNDARY_SLOPE_THRESHOLD: float = get("scoring.boundary_slope_threshold", 0.55)
PASS_THRESHOLD: float = get("scoring.pass_threshold", 60.0)

SCORE_WEIGHTS: dict = get(
    "scoring.weights",
    {
        "word_accuracy": 0.30,
        "prosody": 0.20,
        "rhythm": 0.15,
        "boundary_tone": 0.10,
        "dynamic_stress": 0.10,
        "speed": 0.075,
        "pause": 0.075,
    },
)

GHOST_WORD_CONFIDENCE: float = get("alignment.ghost_word_confidence", 0.1)
CAPTION_FALLBACK_ENABLED: bool = get(
    "alignment.caption_fallback_enabled", True
)
CAPTION_MIN_SURVIVING_WORD_RATIO: float = get(
    "alignment.caption_min_surviving_word_ratio", 0.55
)
CAPTION_STRONG_MIN_SURVIVING_WORD_RATIO: float = get(
    "alignment.caption_strong_min_surviving_word_ratio", 0.35
)
CAPTION_FRONT_WINDOW_WORDS: int = get(
    "alignment.caption_front_window_words", 6
)
CAPTION_MAX_FRONT_LOW_CONF_RATIO: float = get(
    "alignment.caption_max_front_low_conf_ratio", 0.5
)
CAPTION_MAX_LEADING_GAP_SEC: float = get(
    "alignment.caption_max_leading_gap_sec", 1.2
)
CAPTION_FALLBACK_MIN_REASON_COUNT: int = get(
    "alignment.caption_fallback_min_reason_count", 2
)

# ── Vocal Remover (Reference 전용) ──
# false이면 reference 생성에서 vocal separation 및
# VR 기반 prosody 추출 경로를 모두 스킵합니다.
VR_ENABLED: bool = get("vocal_remover.enabled", True)
VR_MODEL: str = get("vocal_remover.model", "htdemucs")
VR_DEVICE: str = get("vocal_remover.device", "auto")
VR_SEGMENT: int | None = get("vocal_remover.segment", None)
REFERENCE_F0_GATE_MIN_VOICED_RATIO: float = get(
    "reference_feature_gate.f0_min_voiced_ratio", 0.5
)
REFERENCE_F0_GATE_MAX_JUMP_RATIO: float = get(
    "reference_feature_gate.f0_max_jump_ratio", 0.2
)
REFERENCE_F0_GATE_MAX_SEMITONE_JUMP: float = get(
    "reference_feature_gate.f0_max_semitone_jump", 6.0
)
REFERENCE_RMS_GATE_MIN_CONTRAST_DB: float = get(
    "reference_feature_gate.rms_min_contrast_db", 6.0
)
REFERENCE_RMS_GATE_MAX_DROPOUT_RATIO: float = get(
    "reference_feature_gate.rms_max_dropout_ratio", 0.8
)
REFERENCE_RMS_GATE_DROPOUT_FRACTION: float = get(
    "reference_feature_gate.rms_dropout_fraction", 0.1
)

S3_BUCKET: str = get("aws.s3.bucket", "test-bucket")

S3_REGION: str = get("aws.s3.region", "test")

S3_ACCESS_KEY: str = get("aws.s3.access-key", "test")

S3_SECRET_KEY: str = get("aws.s3.secret-key", "test")