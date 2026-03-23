"""StyleEcho I/O 통합 함수."""

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
    """입력 문자열이 S3 오브젝트 키인지 판단합니다.

    Args:
        source: ``user_audio`` 원본 입력값.

    Returns:
        S3 오브젝트 키로 보이면 ``True``.
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
    """임시 파일이 존재하면 삭제합니다.

    Args:
        path: 삭제할 파일 경로.
    """
    try:
        if os.path.exists(path):
            os.remove(path)
            logger.info("Removed temp file: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove %s: %s", path, exc)


def remove_dir(path: str) -> None:
    """임시 디렉터리가 존재하면 삭제합니다.

    Args:
        path: 삭제할 디렉터리 경로.
    """
    try:
        if os.path.exists(path):
            shutil.rmtree(path, ignore_errors=True)
            logger.info("Removed temp dir: %s", path)
    except OSError as exc:
        logger.warning("Failed to remove dir %s: %s", path, exc)


def download_audio_from_url(url: str, target_path: str) -> None:
    """원격 오디오 파일 또는 S3 오브젝트를 로컬 경로로 다운로드합니다.

    오디오 캐시가 활성화된 경우 캐시를 먼저 조회합니다.
    캐시 미스 시 정상 다운로드 후 캐시에 저장합니다.

    Args:
        url: 원격 오디오 소스. ``http``, ``https``, ``s3``,
            S3 오브젝트 키 형식을 지원합니다.
        target_path: 로컬 파일 시스템 경로.

    Raises:
        HTTPException: 지원하지 않는 포맷이거나 다운로드 실패 시.
    """
    from integrations.audio_cache import get_audio_cache

    source = url.strip()
    cache = get_audio_cache()

    if cache is not None and cache.get(source, target_path):
        return

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
            if cache is not None:
                cache.put(source, target_path)
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

    if cache is not None:
        cache.put(source, target_path)


def prepare_reference_audio_dir(
    video_id: str,
    start_sec: float,
    end_sec: float,
    save_dir: str | None = None,
) -> str:
    """저장용 레퍼런스 오디오 아티팩트 디렉터리를 생성합니다.

    Args:
        video_id: YouTube 비디오 식별자.
        start_sec: 요청 시작 시간(초).
        end_sec: 요청 종료 시간(초).
        save_dir: 명시적 출력 디렉터리 (선택).

    Returns:
        생성된 디렉터리 경로.
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
    """요청 구간 전체 레퍼런스 오디오를 저장합니다.

    Args:
        audio_array: 요청 구간 오디오 배열.
        sample_rate: ``audio_array`` 의 샘플레이트.
        target_dir: 아티팩트 저장 디렉터리.

    Returns:
        저장된 전체 오디오 경로.
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
    """레퍼런스 오디오 배열에서 파트 단위 WAV 클립을 추출합니다.

    Args:
        audio_array: 소스 레퍼런스 오디오 배열.
        sample_rate: ``audio_array`` 의 샘플레이트.
        start_sec: 클립 시작 시간(초).
        end_sec: 클립 종료 시간(초).
        target_path: 출력 WAV 경로.

    Returns:
        저장된 파트 오디오 경로.
    """
    start_idx = max(0, int(start_sec * sample_rate))
    end_idx = max(start_idx, int(end_sec * sample_rate))
    segment = np.asarray(audio_array[start_idx:end_idx], dtype=np.float32)
    wavfile.write(target_path, sample_rate, segment)
    logger.info("Saved part audio: %s", target_path)
    return target_path
