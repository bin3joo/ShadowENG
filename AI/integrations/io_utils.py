"""StyleEcho I/O integration functions."""

import logging
import os
import shutil
import urllib.parse
import urllib.request
from pathlib import Path

import boto3
import config
import numpy as np
from botocore.exceptions import ClientError, NoCredentialsError
from fastapi import HTTPException
from scipy.io import wavfile

logger = logging.getLogger(__name__)
_PROJECT_TEMP_DIR = Path(__file__).resolve().parents[1] / "temp"


def _is_likely_s3_object_key(source: str) -> bool:
    """Return whether the input string looks like a raw S3 object key.

    Args:
        source: Raw ``user_audio`` input.

    Returns:
        ``True`` if the input appears to be an S3 object key.
    """
    stripped = source.strip()
    if not stripped:
        return False
    if "://" in stripped or stripped.startswith("data:"):
        return False
    if stripped.startswith(("/", ".")):
        return False
    if any(char.isspace() for char in stripped):
        return False

    last_segment = stripped.rsplit("/", 1)[-1]
    return "." in last_segment


def remove_file(path: str) -> None:
    """Remove a temporary file if it exists.

    Args:
        path: File path to remove.
    """
    try:
        if os.path.exists(path):
            os.remove(path)
            logger.info("Removed temp file: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove %s: %s", path, exc)


def remove_dir(path: str) -> None:
    """Remove a temporary directory if it exists.

    Args:
        path: Directory path to remove.
    """
    try:
        if os.path.exists(path):
            shutil.rmtree(path, ignore_errors=True)
            logger.info("Removed temp dir: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove dir %s: %s", path, exc)


def download_audio_from_url(url: str, target_path: str) -> None:
    """Download a remote audio file or S3 object to a local path.

    Args:
        url: Remote audio source. Supports ``http``, ``https``, ``s3``, and
            direct S3 object keys.
        target_path: Local filesystem path to write.

    Raises:
        HTTPException: If the source format is unsupported or the download
            fails.
    """
    source = url.strip()
    parsed = urllib.parse.urlparse(source)

    is_s3 = False
    bucket = config.S3_BUCKET
    key: str | None = None

    if parsed.scheme == "s3":
        is_s3 = True
        bucket = parsed.netloc
        key = parsed.path.lstrip("/")
    elif "s3" in parsed.netloc and "amazonaws.com" in parsed.netloc:
        is_s3 = True
        parts = parsed.netloc.split(".")
        if parts[0] == "s3":
            path_parts = parsed.path.lstrip("/").split("/", 1)
            if len(path_parts) == 2:
                bucket = path_parts[0]
                key = path_parts[1]
        else:
            bucket = parts[0]
            key = parsed.path.lstrip("/")
    elif _is_likely_s3_object_key(source):
        is_s3 = True
        key = source.lstrip("/")
    elif parsed.scheme not in ("http", "https"):
        raise HTTPException(
            status_code=400,
            detail=(
                "user_audio 는 http/https URL, s3 URL, 또는 S3 object key "
                "형식만 지원합니다."
            ),
        )

    if is_s3:
        if not bucket or not key:
            raise HTTPException(
                status_code=400,
                detail="S3 bucket 또는 object key 가 올바르지 않습니다.",
            )
        try:
            s3_client = boto3.client(
                "s3",
                region_name=config.S3_REGION,
                aws_access_key_id=config.S3_ACCESS_KEY,
                aws_secret_access_key=config.S3_SECRET_KEY,
            )
            logger.info(
                "Downloading S3 object: bucket=%s, key=%s", bucket, key
            )
            s3_client.download_file(bucket, key, target_path)
            return
        except (NoCredentialsError, ClientError) as exc:
            logger.error("Failed to download from S3: %s", exc)
            raise HTTPException(
                status_code=400,
                detail=f"S3에서 오디오 다운로드 실패: {exc}",
            )

    with urllib.request.urlopen(source, timeout=30) as response:
        with open(target_path, "wb") as file_obj:
            file_obj.write(response.read())


def prepare_reference_audio_dir(
    video_id: str,
    start_sec: float,
    end_sec: float,
    save_dir: str | None = None,
) -> str:
    """Create a persistent directory for saved reference audio artifacts.

    Args:
        video_id: YouTube video identifier.
        start_sec: Request start time in seconds.
        end_sec: Request end time in seconds.
        save_dir: Optional explicit output directory.

    Returns:
        Created directory path.
    """
    if save_dir:
        target_dir = Path(save_dir)
    else:
        dir_name = f"{video_id}_{int(start_sec * 1000)}_{int(end_sec * 1000)}"
        target_dir = _PROJECT_TEMP_DIR / "reference_audio" / dir_name

    if target_dir.exists():
        shutil.rmtree(target_dir)

    (target_dir / "parts").mkdir(parents=True, exist_ok=True)
    return str(target_dir)


def persist_reference_audio(
    audio_array: np.ndarray,
    sample_rate: int,
    target_dir: str,
) -> str:
    """Persist the full request-local reference audio clip.

    Args:
        audio_array: Request-local audio array.
        sample_rate: Sample rate of ``audio_array``.
        target_dir: Output directory for saved artifacts.

    Returns:
        Saved full-audio path.
    """
    target_path = os.path.join(target_dir, "full_audio.wav")
    segment = np.asarray(audio_array, dtype=np.float32)
    wavfile.write(target_path, sample_rate, segment)
    logger.info("Saved full reference audio: %s", target_path)
    return target_path


def export_part_audio(
    audio_array: np.ndarray,
    sample_rate: int,
    start_sec: float,
    end_sec: float,
    target_path: str,
) -> str:
    """Export a part-level WAV clip from the loaded reference audio array.

    Args:
        audio_array: Source reference audio array.
        sample_rate: Sample rate of ``audio_array``.
        start_sec: Clip start time in seconds.
        end_sec: Clip end time in seconds.
        target_path: Output WAV path.

    Returns:
        Saved part-audio path.
    """
    start_idx = max(0, int(start_sec * sample_rate))
    end_idx = max(start_idx, int(end_sec * sample_rate))
    segment = np.asarray(audio_array[start_idx:end_idx], dtype=np.float32)
    wavfile.write(target_path, sample_rate, segment)
    logger.info("Saved part audio: %s", target_path)
    return target_path
