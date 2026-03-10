"""StyleEcho I/O integration functions."""

import logging
import os
import shutil
import urllib.parse
import urllib.request
from pathlib import Path

import numpy as np
from fastapi import HTTPException
from scipy.io import wavfile

logger = logging.getLogger(__name__)
_PROJECT_TEMP_DIR = Path(__file__).resolve().parents[1] / "temp"


def remove_file(path: str) -> None:
    """Remove a temporary file if it exists."""
    try:
        if os.path.exists(path):
            os.remove(path)
            logger.info("Removed temp file: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove %s: %s", path, exc)


def remove_dir(path: str) -> None:
    """Remove a temporary directory if it exists."""
    try:
        if os.path.exists(path):
            shutil.rmtree(path, ignore_errors=True)
            logger.info("Removed temp dir: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove dir %s: %s", path, exc)


def download_audio_from_url(url: str, target_path: str) -> None:
    """Download a remote audio file to a local temporary path."""
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https"):
        raise HTTPException(
            status_code=400,
            detail="user_audio URL은 http/https 형식만 지원합니다.",
        )

    with urllib.request.urlopen(url, timeout=30) as response:
        with open(target_path, "wb") as file_obj:
            file_obj.write(response.read())


def prepare_reference_audio_dir(
    video_id: str,
    start_sec: float,
    end_sec: float,
    save_dir: str | None = None,
) -> str:
    """Create a persistent directory for saved reference audio artifacts."""
    if save_dir:
        target_dir = Path(save_dir)
    else:
        dir_name = f"{video_id}_{int(start_sec * 1000)}_{int(end_sec * 1000)}"
        target_dir = _PROJECT_TEMP_DIR / "reference_audio" / dir_name

    if target_dir.exists():
        shutil.rmtree(target_dir)

    (target_dir / "parts").mkdir(parents=True, exist_ok=True)
    return str(target_dir)


def persist_reference_audio(source_path: str, target_dir: str) -> str:
    """Copy downloaded full reference audio to the persistent target directory."""
    target_path = os.path.join(target_dir, "full_audio.wav")
    shutil.copy2(source_path, target_path)
    logger.info("Saved full reference audio: %s", target_path)
    return target_path


def export_part_audio(
    audio_array: np.ndarray,
    sample_rate: int,
    start_sec: float,
    end_sec: float,
    target_path: str,
) -> str:
    """Export a part-level WAV clip from the loaded reference audio array."""
    start_idx = max(0, int(start_sec * sample_rate))
    end_idx = max(start_idx, int(end_sec * sample_rate))
    segment = np.asarray(audio_array[start_idx:end_idx], dtype=np.float32)
    wavfile.write(target_path, sample_rate, segment)
    logger.info("Saved part audio: %s", target_path)
    return target_path
