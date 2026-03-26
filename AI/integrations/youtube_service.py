"""StyleEcho YouTube 통합 함수."""

import logging
import os
import subprocess
import sys
from typing import Literal

import config

logger = logging.getLogger(__name__)


def build_youtube_url(video_id: str) -> str:
    """비디오 ID로 YouTube 표준 시청 URL을 생성합니다.

    Args:
        video_id: YouTube 비디오 식별자.

    Returns:
        YouTube 표준 시청 URL.
    """
    return f"https://www.youtube.com/watch?v={video_id}"


def download_reference_audio(
    video_id: str,
    start_sec: float,
    end_sec: float,
    audio_path: str,
    tmp_dir: str,
    audio_padding_sec: float = 0.0,
) -> tuple[str, str]:
    """YouTube 레퍼런스 오디오를 다운로드하고 URL 및 파일 경로를 반환합니다.

    Args:
        video_id: YouTube 비디오 식별자.
        start_sec: 요청 시작 시간(초).
        end_sec: 요청 종료 시간(초).
        audio_path: 대상 WAV 파일 경로.
        tmp_dir: 중간 파일용 임시 디렉터리.
        audio_padding_sec: 오디오 패딩(초).

    Returns:
        ``(youtube_url, actual_audio_path)`` 튜플.

    Raises:
        RuntimeError: yt-dlp 다운로드 실패 시.
        FileNotFoundError: 다운로드된 오디오 파일을 찾을 수 없을 때.
    """
    youtube_url = build_youtube_url(video_id)
    padded_start_sec = max(0.0, start_sec - audio_padding_sec)
    padded_end_sec = end_sec + audio_padding_sec

    # 파일명 추출 (확장자 없이 지정, postprocessor가 알아서 붙임)
    base_outtmpl = audio_path
    if base_outtmpl.endswith(".wav"):
        base_outtmpl = base_outtmpl[:-4]

    download_section = f"*{padded_start_sec:.3f}-{padded_end_sec + 1.0:.3f}"
    command = [
        sys.executable,
        "-m",
        "yt_dlp",
        "--format",
        "m4a/bestaudio/best",
        "--output",
        base_outtmpl,
        "--extract-audio",
        "--audio-format",
        "wav",
        "--download-sections",
        download_section,
        "--force-keyframes-at-cuts",
        "--no-mtime",
        "--concurrent-fragments",
        "5",
        "--socket-timeout",
        "15",
        "--retries",
        "3",
        "--extractor-args",
        "youtube:player_client=android,ios",
        "--downloader-args",
        "ffmpeg:-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5",
        "--no-playlist",
        "--quiet",
        "--no-warnings",
        youtube_url,
    ]

    logger.info(
        "Downloading audio for %s via yt-dlp subprocess", video_id
    )
    try:
        subprocess.run(
            command,
            check=True,
            timeout=60,
            cwd=tmp_dir,
            capture_output=True,
            text=True,
        )
    except subprocess.TimeoutExpired as exc:
        logger.error("yt-dlp timed out after 60s: %s", exc)
        raise RuntimeError("yt-dlp 다운로드 타임아웃(60초)") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or exc.stdout or "").strip()
        logger.error("yt-dlp subprocess 다운로드 실패: %s", stderr)
        raise RuntimeError(f"yt-dlp 다운로드 실패: {stderr}") from exc
    except OSError as exc:
        logger.error("yt-dlp subprocess 실행 실패: %s", exc)
        raise RuntimeError(f"yt-dlp 다운로드 실패: {exc}")

    actual_audio = audio_path
    if not os.path.exists(audio_path):
        for file_name in os.listdir(tmp_dir):
            if file_name.endswith(".wav"):
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
    """요청 시간 범위의 영어 YouTube 자막을 가져옵니다.

    Args:
        video_id: YouTube 비디오 식별자.
        start_sec: 요청 시작 시간(초).
        end_sec: 요청 종료 시간(초).
        padding_sec: 자막 오버랩 시간 패딩.

    Returns:
        ``(caption_text, caption_source)`` 튜플.
        사용 가능한 자막이 없으면 ``caption_text`` 는 ``None``.
    """
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
