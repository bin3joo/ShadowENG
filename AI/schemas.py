"""StyleEcho API schemas."""

import re

from pydantic import BaseModel, Field, field_validator, model_validator

_VIDEO_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
_ALLOWED_AUDIO_FORMATS = {
    "wav",
    "m4a",
    "mp3",
    "webm",
    "ogg",
    "aac",
    "flac",
}


class GenerateReferenceRequest(BaseModel):
    """Request model for generate-reference."""

    video_id: str
    start_sec: float
    end_sec: float
    save_dir: str | None = None

    @field_validator("video_id")
    @classmethod
    def validate_video_id(cls, value: str) -> str:
        """Validate YouTube video ID format."""
        video_id = value.strip()
        if not _VIDEO_ID_RE.fullmatch(video_id):
            raise ValueError(
                "video_id 는 11자리 YouTube ID 형식이어야 합니다."
            )
        return video_id

    @model_validator(mode="after")
    def validate_time_range(self) -> "GenerateReferenceRequest":
        """Validate reference time range."""
        if self.start_sec < 0:
            raise ValueError("start_sec 는 0 이상이어야 합니다.")
        if self.end_sec <= self.start_sec:
            raise ValueError("end_sec 는 start_sec 보다 커야 합니다.")
        if self.save_dir is not None:
            self.save_dir = self.save_dir.strip() or None
        return self


class WordTimestamp(BaseModel):
    """Word-level timestamp data."""

    word: str
    start: float
    end: float
    score: float = Field(ge=0.0, le=1.0)
    speaker: str | None = None

    @model_validator(mode="after")
    def validate_time_range(self) -> "WordTimestamp":
        """Validate word timestamp range."""
        if self.end < self.start:
            raise ValueError("word timestamp end 는 start 이상이어야 합니다.")
        return self


class ProsodyFeatures(BaseModel):
    """Prosody feature arrays."""

    f0_array: list[float]
    rms_array: list[float]


class PartData(BaseModel):
    """Sentence-level reference data."""

    sentence: str
    start_sec: float
    end_sec: float
    duration_sec: float
    word_count: int
    wpm: float
    difficulty_score: float
    difficulty: str
    reductions: list[dict] = Field(default_factory=list)
    key_expressions: list[str] = Field(default_factory=list)
    word_timestamps: list[WordTimestamp] = Field(default_factory=list)
    pause_count: int = 0
    features: ProsodyFeatures | None = None
    part_source: str = "sentence"
    turn_id: int | None = None
    turn_break_reason: str | None = None
    speaker_risk: str = "low"
    dominant_speaker: str | None = None
    speaker_count: int = 0
    audio_path: str | None = None
    sentence_ko: str | None = None
    source_part_ids: list[str] = Field(default_factory=list)


class LearningExpressionData(BaseModel):
    """Learning expression extracted from the reference text."""

    expression: str
    meaning_ko: str
    context: str


class GenerateReferenceResponse(BaseModel):
    """Response model for generate-reference."""

    status: str
    video_id: str
    youtube_url: str
    start_sec: float
    end_sec: float
    final_script: str
    final_script_ko: str | None = None
    stt_method: str
    parts: list[PartData]
    trimmed_word_count: int
    pause_count: int
    active_speech_sec: float
    word_count: int
    reference_quality: str = "good"
    quality_reasons: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    denoise_mode: str = "off"
    speaker_mode: str = "single_speaker_assumed"
    dialog_mode: str = "sentence"
    turn_count: int = 0
    caption_source: str = "none"
    alignment_median_score: float = 0.0
    low_alignment_ratio: float = 0.0
    overlap_risk_ratio: float = 0.0
    speaker_shift_ratio: float = 0.0
    estimated_snr_db: float = 0.0
    noise_level: str = "low"
    speech_ratio: float = 0.0
    diarization_used: bool = False
    detected_speaker_count: int = 0
    full_audio_path: str | None = None
    learning_expressions: list[LearningExpressionData] = Field(
        default_factory=list
    )
    translation_status: str = "skipped"
    translation_retry_count: int = 0
    translation_provider: str | None = None


class EvaluateAudioRequest(BaseModel):
    """Request model for evaluate-audio."""

    user_audio: str
    user_audio_format: str = "wav"
    final_script: str
    features: ProsodyFeatures | None = None
    word_timestamps: list[WordTimestamp] = Field(default_factory=list)

    @field_validator("user_audio_format")
    @classmethod
    def validate_user_audio_format(cls, value: str) -> str:
        """Validate user audio format."""
        audio_format = value.strip().lower()
        if audio_format not in _ALLOWED_AUDIO_FORMATS:
            raise ValueError(
                "user_audio_format 은 wav, m4a, mp3, webm, ogg, aac, flac 중 하나여야 합니다."
            )
        return audio_format

    @field_validator("final_script")
    @classmethod
    def validate_final_script(cls, value: str) -> str:
        """Validate non-empty final script."""
        script = value.strip()
        if not script:
            raise ValueError("final_script 가 비어있습니다.")
        return script


class WordFeedbackItem(BaseModel):
    """Word-level rhythm feedback item."""

    word: str
    status: str
    ref_start_time: float | None = None
    ref_end_time: float | None = None
    user_start_time: float | None = None
    user_end_time: float | None = None


class BoundaryToneDetail(BaseModel):
    """Boundary tone analysis details."""

    ref_slope: float
    user_slope: float
    status: str


class DynamicStressDetail(BaseModel):
    """Dynamic stress analysis details."""

    ref_dynamic_ratio: float
    user_dynamic_ratio: float
    status: str


class EvaluateScores(BaseModel):
    """Aggregated evaluation scores."""

    total_score: float
    word_accuracy: float
    prosody_and_stress: float
    word_rhythm_score: float
    boundary_tone_score: float
    dynamic_stress_score: float
    speed_similarity: float
    pause_similarity: float


class PitchContourFeedback(BaseModel):
    """Word-level pitch contour feedback item."""

    word: str
    ref_direction: str
    ref_start_hz: float
    ref_end_hz: float
    user_direction: str
    user_start_hz: float
    user_end_hz: float
    feedback: str


class EvaluateDetails(BaseModel):
    """Detailed evaluation feedback payload."""

    word_level_feedback: list[WordFeedbackItem]
    boundary_tone_feedback: BoundaryToneDetail
    dynamic_stress_feedback: DynamicStressDetail
    pitch_contour_feedback: list[PitchContourFeedback] = Field(
        default_factory=list
    )


class EvaluateAudioResponse(BaseModel):
    """Response model for evaluate-audio."""

    status: str
    pass_fail: str | None = None
    pass_threshold: float | None = None
    user_transcription: str = ""
    scores: EvaluateScores | None = None
    details: EvaluateDetails | None = None
    message: str | None = None
