"""Thread-safe local file cache for S3 / remote audio downloads.

Provides LRU (size-based) and TTL (time-based) eviction strategies
to avoid redundant downloads for the same audio source.
"""

import hashlib
import logging
import os
import shutil
import threading
import time
from pathlib import Path
from typing import Optional

import config

logger = logging.getLogger(__name__)

_cache_instance: Optional["AudioCache"] = None
_cache_lock = threading.Lock()


class AudioCache:
    """Thread-safe local file cache with LRU + TTL eviction.

    Args:
        cache_dir: Root directory for cached audio files.
        max_size_mb: Maximum total cache size in megabytes.
        ttl_hours: Maximum hours a file can remain unused before eviction.
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
        """Generate a deterministic cache key from the source string.

        Args:
            source: Remote audio URL or S3 object key.

        Returns:
            SHA-256 hex digest of the source string.
        """
        return hashlib.sha256(source.strip().encode("utf-8")).hexdigest()

    def _cache_path(self, cache_key: str) -> Path:
        """Return the full filesystem path for a cache key.

        Args:
            cache_key: SHA-256 hex digest.

        Returns:
            Path object for the cached file.
        """
        return self._cache_dir / cache_key

    def get(self, source: str, target_path: str) -> bool:
        """Try to serve a cached copy of the source audio.

        If the file exists in cache and has not expired, it is copied
        to ``target_path`` and ``True`` is returned. Otherwise
        ``False`` is returned.

        Args:
            source: Remote audio URL or S3 object key.
            target_path: Destination path expected by the caller.

        Returns:
            ``True`` on cache hit, ``False`` on miss or expiry.
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
        """Store a downloaded file in the cache.

        Copies ``downloaded_path`` into the cache directory and runs
        LRU eviction if the total size exceeds the limit.

        Args:
            source: Remote audio URL or S3 object key.
            downloaded_path: Path to the freshly downloaded file.
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
        """Remove oldest-accessed files until total size is within limit.

        Must be called while holding ``self._lock``.
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
        """Remove all files that have exceeded TTL.

        Returns:
            Number of expired files removed.
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


def get_audio_cache() -> Optional[AudioCache]:
    """Return the singleton AudioCache instance if caching is enabled.

    Returns:
        ``AudioCache`` instance or ``None`` if caching is disabled.
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
