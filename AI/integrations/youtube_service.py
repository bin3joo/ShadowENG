"""StyleEcho YouTube integration functions."""

import logging
import os
import subprocess
from typing import Literal

try:
    from .. import config
except ImportError:
    import config

logger = logging.getLogger(__name__)


def build_youtube_url(video_id: str) -> str:
    """Build a canonical YouTube watch URL from a video ID."""
    return f"https://www.youtube.com/watch?v={video_id}"


def build_yt_dlp_command(
    video_id: str,
    start_sec: float,
    end_sec: float,
    audio_path: str,
    audio_padding_sec: float = 0.0,
) -> list[str]:
    """Build the yt-dlp command for reference audio download."""
    padded_start_sec = max(0.0, start_sec - audio_padding_sec)
    padded_end_sec = end_sec + audio_padding_sec
    return [
        "yt-dlp",
        "--no-playlist",
        "-f",
        "bestaudio/best",
        "--force-keyframes-at-cuts",
        "-x",
        "--audio-format",
        "wav",
        "--download-sections",
        f"*{padded_start_sec}-{padded_end_sec}",
        "-o",
        audio_path,
        build_youtube_url(video_id),
    ]


def download_reference_audio(
    video_id: str,
    start_sec: float,
    end_sec: float,
    audio_path: str,
    tmp_dir: str,
    audio_padding_sec: float = 0.0,
) -> tuple[str, str]:
    """Download YouTube reference audio and return canonical URL and file path."""
    youtube_url = build_youtube_url(video_id)
    yt_dlp_cmd = build_yt_dlp_command(
        video_id,
        start_sec,
        end_sec,
        audio_path,
        audio_padding_sec,
    )
    logger.info("Downloading audio for %s: %s", video_id, " ".join(yt_dlp_cmd))
    proc = subprocess.run(
        yt_dlp_cmd,
        capture_output=True,
        text=True,
        timeout=120,
    )
    if proc.returncode != 0:
        logger.error("yt-dlp stderr for %s: %s", video_id, proc.stderr)
        raise RuntimeError(f"yt-dlp 다운로드 실패: {proc.stderr[:500]}")

    actual_audio = audio_path
    if not os.path.exists(audio_path):
        for file_name in os.listdir(tmp_dir):
            actual_audio = os.path.join(tmp_dir, file_name)
            break
        else:
            raise FileNotFoundError(
                "다운로드된 오디오 파일을 찾을 수 없습니다."
            )

    return youtube_url, actual_audio


def fetch_youtube_captions(
    video_id: str,
    start_sec: float,
    end_sec: float,
    padding_sec: float,
) -> tuple[str | None, Literal["manual", "auto", "none"]]:
    """Fetch English YouTube captions for the requested time range."""
    try:
        from youtube_transcript_api import YouTubeTranscriptApi

        ytt = YouTubeTranscriptApi()
        transcript_list = ytt.list(video_id)

        manual = None
        auto = None
        for transcript in transcript_list:
            if transcript.language_code in ("en", "en-US", "en-GB"):
                if not transcript.is_generated:
                    manual = transcript
                    break
                if auto is None:
                    auto = transcript

        if manual is None and auto is not None:
            logger.info(
                "Auto-generated caption found for %s; falling back to STT",
                video_id,
            )
            return None, "auto"

        transcript = manual
        if transcript is None:
            logger.info("No English caption available for %s", video_id)
            return None, "none"

        caption_source: Literal["manual", "auto", "none"]
        effective_padding_sec: float
        if transcript.is_generated:
            caption_source = "auto"
            effective_padding_sec = 0.0
        else:
            caption_source = "manual"
            effective_padding_sec = padding_sec

        logger.info("Using %s caption for %s", caption_source, video_id)

        entries = transcript.fetch()
        padded_start = max(0.0, start_sec - effective_padding_sec)
        padded_end = end_sec + effective_padding_sec

        words: list[str] = []
        for entry in entries:
            entry_start = entry.start
            entry_end = entry_start + entry.duration
            overlap_start = max(entry_start, padded_start)
            overlap_end = min(entry_end, padded_end)
            overlap_duration = max(0.0, overlap_end - overlap_start)
            entry_duration = max(entry.duration, 1e-6)
            overlap_ratio = overlap_duration / entry_duration
            entry_midpoint = entry_start + (entry.duration / 2.0)
            midpoint_in_window = padded_start <= entry_midpoint <= padded_end

            if overlap_duration > 0 and (
                midpoint_in_window
                or overlap_ratio >= config.CAPTION_MIN_ENTRY_OVERLAP_RATIO
            ):
                words.append(entry.text.strip())

        if not words:
            logger.warning(
                "caption 구간 [%.1f, %.1f] 내 텍스트 없음 (%s)",
                padded_start,
                padded_end,
                video_id,
            )
            return None, caption_source

        caption_text = " ".join(words)
        logger.info(
            "caption fetched for %s: %.0f~%.0fs (pad %.0fs) → %d자",
            video_id,
            padded_start,
            padded_end,
            effective_padding_sec,
            len(caption_text),
        )
        return caption_text, caption_source
    except ImportError:
        logger.warning("youtube-transcript-api 미설치. STT 폴백 사용.")
        return None, "none"
    except Exception as exc:
        logger.warning(
            "caption fetch 실패 (%s, %s): %s",
            video_id,
            type(exc).__name__,
            exc,
        )
        return None, "none"
