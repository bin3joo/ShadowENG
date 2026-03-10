"""Use-case service for user audio evaluation."""

import base64
import binascii
import logging
import os
import tempfile

from fastapi import BackgroundTasks, HTTPException

try:
    from ..integrations.io_utils import download_audio_from_url, remove_file
    from ..pipeline import get_pipeline
    from ..schemas import EvaluateAudioRequest
except ImportError:
    from integrations.io_utils import download_audio_from_url, remove_file
    from pipeline import get_pipeline
    from schemas import EvaluateAudioRequest

logger = logging.getLogger(__name__)


def evaluate_audio_request(
    req: EvaluateAudioRequest,
    background_tasks: BackgroundTasks,
) -> dict:
    """Evaluate a user audio sample against a reference payload."""
    tmp_user_path: str | None = None

    try:
        suffix = f".{req.user_audio_format}"
        tmp_fd, tmp_user_path = tempfile.mkstemp(
            suffix=suffix,
            prefix="styleecho_user_",
        )
        os.close(tmp_fd)

        audio_str = req.user_audio.strip()

        if audio_str.startswith(("http://", "https://")):
            download_audio_from_url(audio_str, tmp_user_path)
            logger.info("Downloaded user audio from URL: %s", audio_str[:80])
        else:
            try:
                audio_bytes = base64.b64decode(audio_str, validate=True)
            except binascii.Error as exc:
                raise HTTPException(
                    status_code=400,
                    detail="user_audio base64 디코딩에 실패했습니다.",
                ) from exc
            with open(tmp_user_path, "wb") as file_obj:
                file_obj.write(audio_bytes)
            logger.info(
                "Decoded base64 user audio (%d bytes)", len(audio_bytes)
            )

        ref_data = {
            "final_script": req.final_script,
            "features": req.features.model_dump() if req.features else {},
            "word_timestamps": [wt.model_dump() for wt in req.word_timestamps],
        }

        pipeline = get_pipeline()
        result = pipeline.evaluate(tmp_user_path, ref_data)
        background_tasks.add_task(remove_file, tmp_user_path)
        return result

    except HTTPException:
        if tmp_user_path and os.path.exists(tmp_user_path):
            remove_file(tmp_user_path)
        raise
    except Exception as exc:
        logger.exception("evaluate-audio failed")
        if tmp_user_path and os.path.exists(tmp_user_path):
            remove_file(tmp_user_path)
        raise HTTPException(status_code=500, detail=str(exc))
