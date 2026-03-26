"""유저 오디오 평가 유스케이스 서비스."""

import base64
import binascii
import logging
import os
import tempfile
from typing import Any

from domain.scoring.evaluation_errors import (
    ERROR_AUDIO_INPUT_FORMAT_INVALID,
    ERROR_AUDIO_TOO_SHORT,
    ERROR_EVALUATION_INTERNAL,
    EVALUATION_INTERNAL_ERROR_MESSAGE,
    INVALID_AUDIO_INPUT_FORMAT_MESSAGE,
    MIN_EVALUATION_AUDIO_BYTES,
    build_http_error_detail,
)
from fastapi import BackgroundTasks, HTTPException
from integrations.io_utils import (
    _is_likely_s3_object_key,
    download_audio_from_url,
    remove_file,
)
from pipeline import get_pipeline
from schemas import EvaluateAudioRequest
from services.request_trace_service import (
    create_trace_context,
    persist_request_trace,
)

logger = logging.getLogger(__name__)


def _summarize_evaluation_result(result: dict[str, Any]) -> dict[str, Any]:
    """Select compact but meaningful evaluation features for trace storage."""
    return {
        "status": result.get("status"),
        "error_code": result.get("error_code"),
        "pass_fail": result.get("pass_fail"),
        "scores": result.get("scores", {}),
        "details": {
            "word_feedback_count": len(
                result.get("details", {}).get("word_level_feedback", [])
            ),
            "boundary_status": result.get("details", {})
            .get("boundary_tone_feedback", {})
            .get("status"),
            "dynamic_status": result.get("details", {})
            .get("dynamic_stress_feedback", {})
            .get("status"),
            "pitch_feedback_count": len(
                result.get("details", {}).get("pitch_contour_feedback", [])
            ),
        },
    }


def evaluate_audio_request(
    req: EvaluateAudioRequest,
    background_tasks: BackgroundTasks,
) -> dict[str, Any]:
    """유저 오디오를 레퍼런스 페이로드 기준으로 평가합니다.

    Args:
        req: 유저 오디오 평가 요청.
        background_tasks: FastAPI 백그라운드 태스크 레지스트리.

    Returns:
        직렬화된 평가 응답 페이로드.

    Raises:
        HTTPException: 요청 페이로드가 유효하지 않거나 평가 실패 시.
    """
    tmp_user_path: str | None = None
    trace_context = create_trace_context("/api/v1/evaluate-audio")
    trace_intermediate: dict[str, Any] = {
        "request_summary": {
            "user_audio_format": req.user_audio_format,
            "final_script_word_count": len(req.final_script.split()),
            "reference_word_count": len(req.word_timestamps),
            "ref_f0_frames": len(req.features.f0_array) if req.features else 0,
            "ref_rms_frames": len(req.features.rms_array) if req.features else 0,
            "hop_length": req.hop_length,
        }
    }

    try:
        suffix = f".{req.user_audio_format}"
        tmp_fd, tmp_user_path = tempfile.mkstemp(
            suffix=suffix,
            prefix="styleecho_user_",
        )
        os.close(tmp_fd)

        audio_str = req.user_audio.strip()
        input_source_type = "base64"

        if audio_str.startswith(("http://", "https://", "s3://")):
            download_audio_from_url(audio_str, tmp_user_path)
            logger.info("Downloaded user audio from URL: %s", audio_str[:80])
            input_source_type = "url"
        elif _is_likely_s3_object_key(audio_str):
            download_audio_from_url(audio_str, tmp_user_path)
            logger.info(
                "Downloaded user audio from configured S3 key: %s",
                audio_str[:80],
            )
            input_source_type = "s3_key"
        else:
            try:
                audio_bytes = base64.b64decode(audio_str, validate=True)
            except binascii.Error:
                error_detail = build_http_error_detail(
                    ERROR_AUDIO_INPUT_FORMAT_INVALID,
                    INVALID_AUDIO_INPUT_FORMAT_MESSAGE,
                )
                persist_request_trace(
                    trace_context=trace_context,
                    request_payload=trace_intermediate["request_summary"],
                    intermediate=trace_intermediate,
                    error_payload={
                        "status_code": 400,
                        "detail": error_detail,
                    },
                )
                raise HTTPException(
                    status_code=400,
                    detail=error_detail,
                )
            else:
                with open(tmp_user_path, "wb") as file_obj:
                    file_obj.write(audio_bytes)
                logger.info(
                    "Decoded base64 user audio (%d bytes)",
                    len(audio_bytes),
                )
                trace_intermediate["decoded_audio_bytes"] = len(audio_bytes)

        file_size = os.path.getsize(tmp_user_path)
        trace_intermediate["audio_input"] = {
            "source_type": input_source_type,
            "file_size_bytes": file_size,
            "suffix": suffix,
        }
        if file_size < MIN_EVALUATION_AUDIO_BYTES:
            logger.warning(
                "Rejected invalid user audio before evaluation: %d bytes",
                file_size,
            )
            error_detail = build_http_error_detail(
                ERROR_AUDIO_TOO_SHORT,
                f"File size is under {MIN_EVALUATION_AUDIO_BYTES} bytes.",
            )
            persist_request_trace(
                trace_context=trace_context,
                request_payload=trace_intermediate["request_summary"],
                intermediate=trace_intermediate,
                error_payload={
                    "status_code": 400,
                    "detail": error_detail,
                },
            )
            raise HTTPException(
                status_code=400,
                detail=error_detail,
            )

        ref_data = {
            "final_script": req.final_script,
            "features": req.features.model_dump() if req.features else {},
            "word_timestamps": [wt.model_dump() for wt in req.word_timestamps],
            "hop_length": req.hop_length,
        }

        pipeline = get_pipeline()
        result = pipeline.evaluate(tmp_user_path, ref_data)
        trace_intermediate["evaluation_result"] = _summarize_evaluation_result(
            result
        )
        persist_request_trace(
            trace_context=trace_context,
            request_payload=trace_intermediate["request_summary"],
            intermediate=trace_intermediate,
            response_payload=_summarize_evaluation_result(result),
        )
        background_tasks.add_task(remove_file, tmp_user_path)
        return result

    except HTTPException:
        if tmp_user_path and os.path.exists(tmp_user_path):
            remove_file(tmp_user_path)
        raise
    except Exception:
        logger.exception("evaluate-audio failed")
        if tmp_user_path and os.path.exists(tmp_user_path):
            remove_file(tmp_user_path)
        error_detail = build_http_error_detail(
            ERROR_EVALUATION_INTERNAL,
            EVALUATION_INTERNAL_ERROR_MESSAGE,
        )
        persist_request_trace(
            trace_context=trace_context,
            request_payload=trace_intermediate["request_summary"],
            intermediate=trace_intermediate,
            error_payload={
                "status_code": 500,
                "detail": error_detail,
            },
        )
        raise HTTPException(
            status_code=500,
            detail=error_detail,
        ) from None
