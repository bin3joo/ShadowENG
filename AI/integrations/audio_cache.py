"""S3 / 원격 오디오 다운로드용 스레드 안전 로컬 파일 캐시.

LRU(용량 기반) 및 TTL(시간 기반) 제거 전략을 제공하여
동일 오디오 소스의 중복 다운로드를 방지합니다.
"""

from __future__ import annotations

import hashlib
import logging
import os
import shutil
import threading
import time
from pathlib import Path

import config

logger = logging.getLogger(__name__)

_cache_instance: AudioCache | None = None
_cache_lock = threading.Lock()


class AudioCache:
    """LRU + TTL 제거 전략을 적용한 스레드 안전 로컬 파일 캐시.

    Args:
        cache_dir: 캐시 오디오 파일 루트 디렉터리.
        max_size_mb: 최대 캐시 용량(MB).
        ttl_hours: 제거 전 최대 미사용 시간(시간).
    """

    def __init__(
        self,
        cache_dir: str,
        max_size_mb: int = 500,
        ttl_hours: int = 24,
    ) -> None:
        self._cache_dir = Path(cache_dir)
        self._max_size_bytes = max_size_mb * 1024 * 1024
        self._ttl_seconds = ttl_hours * 3600
        self._lock = threading.Lock()
        self._cache_dir.mkdir(parents=True, exist_ok=True)
        logger.info(
            "AudioCache initialized: dir=%s, max=%dMB, ttl=%dh",
            self._cache_dir,
            max_size_mb,
            ttl_hours,
        )

    @staticmethod
    def _make_cache_key(source: str) -> str:
        """소스 문자열로부터 결정적 캐시 키를 생성합니다.

        Args:
            source: 원격 오디오 URL 또는 S3 오브젝트 키.

        Returns:
            소스 문자열의 SHA-256 헥스 다이제스트.
        """
        return hashlib.sha256(source.strip().encode("utf-8")).hexdigest()

    def _cache_path(self, cache_key: str) -> Path:
        """캐시 키에 대한 전체 파일 시스템 경로를 반환합니다.

        Args:
            cache_key: SHA-256 헥스 다이제스트.

        Returns:
            캐시된 파일의 ``Path`` 객체.
        """
        return self._cache_dir / cache_key

    def get(self, source: str, target_path: str) -> bool:
        """캐시된 소스 오디오 복사본 제공을 시도합니다.

        캐시에 파일이 존재하고 만료되지 않았으면 ``target_path`` 로
        복사하고 ``True`` 를 반환합니다. 그렇지 않으면
        ``False`` 를 반환합니다.

        Args:
            source: 원격 오디오 URL 또는 S3 오브젝트 키.
            target_path: 호출자가 기대하는 대상 경로.

        Returns:
            캐시 히트 시 ``True``, 미스 또는 만료 시 ``False``.
        """
        cache_key = self._make_cache_key(source)
        cached = self._cache_path(cache_key)

        with self._lock:
            if not cached.exists():
                return False

            age_seconds = time.time() - cached.stat().st_mtime
            if age_seconds > self._ttl_seconds:
                logger.info(
                    "Cache expired (age=%.0fs): %s",
                    age_seconds,
                    source[:80],
                )
                cached.unlink(missing_ok=True)
                return False

            try:
                shutil.copy2(str(cached), target_path)
                os.utime(str(cached))
                logger.info("Cache hit: %s", source[:80])
                return True
            except OSError as exc:
                logger.warning("Cache read failed: %s", exc)
                return False

    def put(self, source: str, downloaded_path: str) -> None:
        """다운로드된 파일을 캐시에 저장합니다.

        ``downloaded_path`` 를 캐시 디렉터리에 복사하고 총 용량이
        한도를 초과하면 LRU 제거를 실행합니다.

        Args:
            source: 원격 오디오 URL 또는 S3 오브젝트 키.
            downloaded_path: 새로 다운로드된 파일 경로.
        """
        cache_key = self._make_cache_key(source)
        cached = self._cache_path(cache_key)

        with self._lock:
            try:
                shutil.copy2(downloaded_path, str(cached))
                logger.info("Cache stored: %s", source[:80])
            except OSError as exc:
                logger.warning("Cache write failed: %s", exc)
                return

            self._evict_if_needed()

    def _evict_if_needed(self) -> None:
        """총 용량이 한도 이내가 될 때까지 가장 오래된 파일을 제거합니다.

        ``self._lock`` 을 보유한 상태에서 호출해야 합니다.
        """
        entries = []
        total_size = 0
        for entry in self._cache_dir.iterdir():
            if entry.name.startswith("."):
                continue
            if entry.is_file():
                stat = entry.stat()
                entries.append((entry, stat.st_mtime, stat.st_size))
                total_size += stat.st_size

        if total_size <= self._max_size_bytes:
            return

        entries.sort(key=lambda e: e[1])

        evicted_count = 0
        for entry_path, _, entry_size in entries:
            if total_size <= self._max_size_bytes:
                break
            try:
                entry_path.unlink()
                total_size -= entry_size
                evicted_count += 1
            except OSError:
                pass

        if evicted_count:
            logger.info(
                "Cache LRU eviction: removed %d files, "
                "remaining %.1fMB / %.1fMB",
                evicted_count,
                total_size / (1024 * 1024),
                self._max_size_bytes / (1024 * 1024),
            )

    def cleanup_expired(self) -> int:
        """TTL을 초과한 모든 파일을 제거합니다.

        Returns:
            제거된 만료 파일 수.
        """
        removed = 0
        now = time.time()

        with self._lock:
            for entry in self._cache_dir.iterdir():
                if entry.name.startswith("."):
                    continue
                if not entry.is_file():
                    continue
                age = now - entry.stat().st_mtime
                if age > self._ttl_seconds:
                    try:
                        entry.unlink()
                        removed += 1
                    except OSError:
                        pass

        if removed:
            logger.info("Cache TTL cleanup: removed %d expired files", removed)
        return removed


def get_audio_cache() -> AudioCache | None:
    """캐시가 활성화된 경우 싱글턴 AudioCache 인스턴스를 반환합니다.

    Returns:
        ``AudioCache`` 인스턴스 또는 캐시 비활성화 시 ``None``.
    """
    if not config.AUDIO_CACHE_ENABLED:
        return None

    global _cache_instance
    if _cache_instance is not None:
        return _cache_instance

    with _cache_lock:
        if _cache_instance is None:
            cache_dir = config.AUDIO_CACHE_DIR
            if not os.path.isabs(cache_dir):
                cache_dir = str(
                    Path(__file__).resolve().parents[1] / cache_dir
                )
            _cache_instance = AudioCache(
                cache_dir=cache_dir,
                max_size_mb=config.AUDIO_CACHE_MAX_SIZE_MB,
                ttl_hours=config.AUDIO_CACHE_TTL_HOURS,
            )
    return _cache_instance
