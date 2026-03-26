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

logger = logging.getLogger(__name__)


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

    try:
        suffix = f".{req.user_audio_format}"
        tmp_fd, tmp_user_path = tempfile.mkstemp(
            suffix=suffix,
            prefix="styleecho_user_",
        )
        os.close(tmp_fd)

        audio_str = req.user_audio.strip()

        if audio_str.startswith(("http://", "https://", "s3://")):
            download_audio_from_url(audio_str, tmp_user_path)
            logger.info("Downloaded user audio from URL: %s", audio_str[:80])
        elif _is_likely_s3_object_key(audio_str):
            download_audio_from_url(audio_str, tmp_user_path)
            logger.info(
                "Downloaded user audio from configured S3 key: %s",
                audio_str[:80],
            )
        else:
            try:
                audio_bytes = base64.b64decode(audio_str, validate=True)
            except binascii.Error:
                raise HTTPException(
                    status_code=400,
                    detail=build_http_error_detail(
                        ERROR_AUDIO_INPUT_FORMAT_INVALID,
                        INVALID_AUDIO_INPUT_FORMAT_MESSAGE,
                    ),
                )
            else:
                with open(tmp_user_path, "wb") as file_obj:
                    file_obj.write(audio_bytes)
                logger.info(
                    "Decoded base64 user audio (%d bytes)",
                    len(audio_bytes),
                )

        file_size = os.path.getsize(tmp_user_path)
        if file_size < MIN_EVALUATION_AUDIO_BYTES:
            logger.warning(
                "Rejected invalid user audio before evaluation: %d bytes",
                file_size,
            )
            raise HTTPException(
                status_code=400,
                detail=build_http_error_detail(
                    ERROR_AUDIO_TOO_SHORT,
                    f"File size is under {MIN_EVALUATION_AUDIO_BYTES} bytes.",
                ),
            )

        ref_data = {
            "final_script": req.final_script,
            "features": req.features.model_dump() if req.features else {},
            "word_timestamps": [wt.model_dump() for wt in req.word_timestamps],
            "hop_length": req.hop_length,
        }

        pipeline = get_pipeline()
        result = pipeline.evaluate(tmp_user_path, ref_data)
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
        raise HTTPException(
            status_code=500,
            detail=build_http_error_detail(
                ERROR_EVALUATION_INTERNAL,
                EVALUATION_INTERNAL_ERROR_MESSAGE,
            ),
        ) from None
