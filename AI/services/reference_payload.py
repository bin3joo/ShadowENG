"""Reference payload helper functions."""

import re

import numpy as np

try:
    from ..domain.processing.engine_utils import (
        _sum_word_durations,
        count_pauses_from_words,
    )
    from ..integrations.youtube_service import build_youtube_url
except ImportError:
    from domain.processing.engine_utils import (
        _sum_word_durations,
        count_pauses_from_words,
    )
    from integrations.youtube_service import build_youtube_url

_DISALLOWED_SCRIPT_CHAR_RE = re.compile(r"[^A-Za-z0-9\s'.,!?]")
_MULTI_SPACE_RE = re.compile(r"\s+")
_SPACE_BEFORE_PUNCT_RE = re.compile(r"\s+([.,!?])")


def _numpy_to_python(obj):
    """Convert NumPy values into JSON-serializable Python values."""
    if isinstance(obj, np.integer):
        return int(obj)
    if isinstance(obj, np.floating):
        return float(obj)
    if isinstance(obj, np.ndarray):
        return obj.tolist()
    return obj


def sanitize_reference_text(text: str) -> str:
    """Sanitize transcript text while keeping basic sentence punctuation."""
    normalized = text.replace("\r", " ").replace("\n", " ")
    normalized = _DISALLOWED_SCRIPT_CHAR_RE.sub(" ", normalized)
    normalized = _MULTI_SPACE_RE.sub(" ", normalized).strip()
    normalized = _SPACE_BEFORE_PUNCT_RE.sub(r"\1", normalized)
    return normalized


def sanitize_word_timestamps(words: list[dict]) -> list[dict]:
    """Sanitize word timestamp text and drop punctuation-only entries."""
    sanitized_words: list[dict] = []
    for word in words:
        clean_word = sanitize_reference_text(word.get("word", ""))
        if not clean_word:
            continue

        sanitized_word = dict(word)
        sanitized_word["word"] = clean_word
        sanitized_words.append(sanitized_word)

    return sanitized_words


def attach_part_analysis(
    sentence_data: list[dict],
    f0: np.ndarray,
    rms: np.ndarray,
    speech_start_sec: float,
    target_sr: int,
    hop_length: int,
) -> list[dict]:
    """Attach prosody features and pause counts to sentence parts."""
    for part in sentence_data:
        part_words = part.get("word_timestamps", [])
        word_starts = [
            word.get("start") for word in part_words if "start" in word
        ]
        word_ends = [word.get("end") for word in part_words if "end" in word]

        part_start_sec = min(word_starts) if word_starts else part["start_sec"]
        part_end_sec = max(word_ends) if word_ends else part["end_sec"]

        rel_start = max(0.0, part_start_sec - speech_start_sec)
        rel_end = max(rel_start, part_end_sec - speech_start_sec)

        frame_start = int(rel_start * target_sr / hop_length)
        frame_end = int(rel_end * target_sr / hop_length)

        part["features"] = {
            "f0_array": _numpy_to_python(f0[frame_start:frame_end]),
            "rms_array": _numpy_to_python(rms[frame_start:frame_end]),
        }
        part["pause_count"] = count_pauses_from_words(part_words)

    return sentence_data


def build_reference_response(
    video_id: str,
    start_sec: float,
    end_sec: float,
    final_script: str,
    stt_method: str,
    sentence_data: list[dict],
    trimmed_word_count: int,
    final_words: list[dict],
    quality_metadata: dict | None = None,
    full_audio_path: str | None = None,
    translation_metadata: dict | None = None,
) -> dict:
    """Build generate-reference response payload."""
    payload = {
        "status": "SUCCESS",
        "video_id": video_id,
        "youtube_url": build_youtube_url(video_id),
        "start_sec": start_sec,
        "end_sec": end_sec,
        "final_script": final_script,
        "final_script_ko": None,
        "stt_method": stt_method,
        "parts": sentence_data,
        "trimmed_word_count": trimmed_word_count,
        "pause_count": count_pauses_from_words(final_words),
        "active_speech_sec": round(_sum_word_durations(final_words), 3),
        "word_count": len(final_script.split()),
        "full_audio_path": full_audio_path,
        "learning_expressions": [],
        "translation_status": "skipped",
        "translation_retry_count": 0,
        "translation_provider": None,
    }
    if quality_metadata:
        payload.update(quality_metadata)
    if translation_metadata:
        payload.update(translation_metadata)
    return payload
