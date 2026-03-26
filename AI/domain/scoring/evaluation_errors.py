"""Evaluation error helpers shared by API and scoring pipeline."""

MIN_EVALUATION_AUDIO_BYTES = 4096

ERROR_AUDIO_INPUT_FORMAT_INVALID = "AUDIO_INPUT_FORMAT_INVALID"
ERROR_AUDIO_TOO_SHORT = "AUDIO_TOO_SHORT"
ERROR_AUDIO_LOAD_FAILED = "AUDIO_LOAD_FAILED"
ERROR_NO_VOICE_DETECTED = "NO_VOICE_DETECTED"
ERROR_EVALUATION_INTERNAL = "EVALUATION_INTERNAL_ERROR"

INVALID_AUDIO_MESSAGE = (
    "오디오를 분석할 수 없습니다. 녹음이 너무 짧거나 손상되었습니다. "
    "다시 녹음해주세요."
)
NO_VOICE_DETECTED_MESSAGE = (
    "음성이 인식되지 않았습니다. 마이크 상태를 확인하고 다시 녹음해주세요."
)
INVALID_AUDIO_INPUT_FORMAT_MESSAGE = (
    "user_audio는 base64, http/https URL, s3 URL, 또는 S3 object key 형식만 지원합니다."
)
EVALUATION_INTERNAL_ERROR_MESSAGE = (
    "오디오 평가 중 내부 오류가 발생했습니다."
)


def build_fail_response(
    error_code: str,
    message: str,
) -> dict[str, str]:
    """Return the standard FAIL payload for evaluation errors."""
    return {
        "status": "FAIL",
        "error_code": error_code,
        "message": message,
    }


def build_invalid_audio_fail_response() -> dict[str, str]:
    """Return the standard FAIL payload for invalid user audio."""
    return build_fail_response(ERROR_AUDIO_LOAD_FAILED, INVALID_AUDIO_MESSAGE)


def build_no_voice_fail_response() -> dict[str, str]:
    """Return the standard FAIL payload for no detectable speech."""
    return build_fail_response(
        ERROR_NO_VOICE_DETECTED,
        NO_VOICE_DETECTED_MESSAGE,
    )


def build_http_error_detail(
    error_code: str,
    reason: str,
) -> dict[str, str]:
    """Return a consistent FastAPI HTTPException detail payload."""
    return {
        "error_code": error_code,
        "reason": reason,
    }
