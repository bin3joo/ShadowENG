"""StyleEcho API 스키마 정의."""

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
    """generate-reference 요청 모델."""

    video_id: str
    start_sec: float
    end_sec: float
    save_dir: str | None = None

    @field_validator("video_id")
    @classmethod
    def validate_video_id(cls, value: str) -> str:
        """YouTube 비디오 ID 형식을 검증합니다."""
        video_id = value.strip()
        if not _VIDEO_ID_RE.fullmatch(video_id):
            raise ValueError(
                "video_id 는 11자리 YouTube ID 형식이어야 합니다."
            )
        return video_id

    @model_validator(mode="after")
    def validate_time_range(self) -> "GenerateReferenceRequest":
        """레퍼런스 시간 범위를 검증합니다."""
        if self.start_sec < 0:
            raise ValueError("start_sec 는 0 이상이어야 합니다.")
        if self.end_sec <= self.start_sec:
            raise ValueError("end_sec 는 start_sec 보다 커야 합니다.")
        if self.save_dir is not None:
            self.save_dir = self.save_dir.strip() or None
        return self


class WordTimestamp(BaseModel):
    """단어 레벨 타임스탬프 데이터."""

    word: str
    start: float
    end: float
    score: float | None = Field(default=None, ge=0.0, le=1.0)
    speaker: str | None = None

    @model_validator(mode="after")
    def validate_time_range(self) -> "WordTimestamp":
        """단어 타임스탬프 범위를 검증합니다."""
        if self.end < self.start:
            raise ValueError("word timestamp end 는 start 이상이어야 합니다.")
        return self


class ProsodyFeatures(BaseModel):
    """억양 특징 배열."""

    f0_array: list[float]
    rms_array: list[float]


class ReferenceWordTimestamp(BaseModel):
    """생성된 레퍼런스의 공개용 단어 타임스탬프 데이터."""

    word: str
    start: float
    end: float

    @model_validator(mode="after")
    def validate_time_range(self) -> "ReferenceWordTimestamp":
        """단어 타임스탬프 범위를 검증합니다."""
        if self.end < self.start:
            raise ValueError("word timestamp end 는 start 이상이어야 합니다.")
        return self


class PartVocabularyData(BaseModel):
    """파트별 학습 지원용 어휘 항목."""

    word: str
    meaning_ko: str
    phonetic_en: str
    phonetic_ko: str
    example_en: str
    example_ko: str


class ReferencePartData(BaseModel):
    """공개용 문장 단위 레퍼런스 데이터."""

    sentence: str
    start_sec: float
    end_sec: float
    duration_sec: float
    difficulty_score: float
    difficulty: str
    key_expressions: list[str] = Field(default_factory=list)
    word_timestamps: list[ReferenceWordTimestamp] = Field(default_factory=list)
    pause_count: int = 0
    features: ProsodyFeatures | None = None
    sentence_ko: str | None = None
    source_part_ids: list[str] = Field(default_factory=list)
    vocabulary: list[PartVocabularyData] = Field(default_factory=list)


class LearningExpressionData(BaseModel):
    """레퍼런스 텍스트에서 추출된 학습 표현."""

    expression: str
    meaning: str
    pronunciation_en: str
    pronunciation_ko: str
    nuance_in_sentence: str
    example_en: str
    example_ko: str


class GenerateReferenceResponse(BaseModel):
    """generate-reference 응답 모델."""

    status: str
    video_id: str
    start_sec: float
    end_sec: float
    final_script: str
    final_script_ko: str | None = None
    parts: list[ReferencePartData]
    trimmed_word_count: int
    pause_count: int
    active_speech_sec: float
    word_count: int
    reference_quality: str = "good"
    quality_reasons: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    learning_expressions: list[LearningExpressionData] = Field(
        default_factory=list
    )
    translation_success: bool = False
    translation_retry_count: int = 0
    translation_provider: str | None = None
    hop_length: int | None = None


class EvaluateAudioRequest(BaseModel):
    """evaluate-audio 요청 모델."""

    user_audio: str
    user_audio_format: str = "wav"
    final_script: str
    features: ProsodyFeatures | None = None
    word_timestamps: list[WordTimestamp] = Field(default_factory=list)
    hop_length: int | None = None

    @field_validator("user_audio_format")
    @classmethod
    def validate_user_audio_format(cls, value: str) -> str:
        """유저 오디오 포맷을 검증합니다."""
        audio_format = value.strip().lower()
        if audio_format not in _ALLOWED_AUDIO_FORMATS:
            raise ValueError(
                "user_audio_format 은 wav, m4a, mp3, webm, ogg, aac, flac 중 하나여야 합니다."
            )
        return audio_format

    @field_validator("final_script")
    @classmethod
    def validate_final_script(cls, value: str) -> str:
        """final_script 가 비어있지 않은지 검증합니다."""
        script = value.strip()
        if not script:
            raise ValueError("final_script 가 비어있습니다.")
        return script


class WordFeedbackItem(BaseModel):
    """단어 레벨 리듬 피드백 항목."""

    word: str
    status: str
    ref_start_time: float | None = None
    ref_end_time: float | None = None
    user_start_time: float | None = None
    user_end_time: float | None = None


class BoundaryToneDetail(BaseModel):
    """종결 억양 분석 상세."""

    ref_slope: float
    user_slope: float
    status: str


class DynamicStressDetail(BaseModel):
    """동적 강세 분석 상세."""

    ref_dynamic_ratio: float
    user_dynamic_ratio: float
    status: str


class EvaluateScores(BaseModel):
    """집계된 평가 점수."""

    total_score: float
    word_accuracy: float
    prosody_and_stress: float
    word_rhythm_score: float
    boundary_tone_score: float
    dynamic_stress_score: float
    speed_similarity: float
    pause_similarity: float


class PitchContourFeedback(BaseModel):
    """단어 레벨 피치 컨투어 피드백 항목."""

    word: str
    ref_direction: str
    ref_start_hz: float
    ref_end_hz: float
    user_direction: str
    user_start_hz: float
    user_end_hz: float
    feedback: str


class EvaluateDetails(BaseModel):
    """상세 평가 피드백 페이로드."""

    word_level_feedback: list[WordFeedbackItem]
    boundary_tone_feedback: BoundaryToneDetail
    dynamic_stress_feedback: DynamicStressDetail
    pitch_contour_feedback: list[PitchContourFeedback] = Field(
        default_factory=list
    )


class EvaluateAudioResponse(BaseModel):
    """evaluate-audio 응답 모델."""

    status: str
    pass_fail: str | None = None
    pass_threshold: float | None = None
    user_transcription: str = ""
    scores: EvaluateScores | None = None
    details: EvaluateDetails | None = None
    message: str | None = None
