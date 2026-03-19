"""
StyleEcho 오디오 처리
=====================
분석용 디노이징(Track B)과 구간 경계 정제(trim) 로직을 담당합니다.
"""

import logging
import os
import re
from typing import Any

import config
import noisereduce as nr
import numpy as np

logger = logging.getLogger(__name__)


def denoise_for_analysis(
    y: np.ndarray,
    sr: int,
    profile: str | None = None,
) -> np.ndarray:
    """Apply denoising for prosody analysis only.

    Args:
        y: Input audio array.
        sr: Sample rate of ``y``.
        profile: Optional denoise intensity profile.

    Returns:
        Denoised audio array.
    """
    if not config.DENOISE_ENABLED or profile == "off":
        return y

    stationary = config.DENOISE_STATIONARY
    prop_decrease = config.DENOISE_PROP_DECREASE
    if profile == "mild":
        prop_decrease = min(prop_decrease, 0.55)
    elif profile == "moderate":
        stationary = False
        prop_decrease = max(prop_decrease, 0.88)

    return nr.reduce_noise(
        y=y,
        sr=sr,
        stationary=stationary,
        prop_decrease=prop_decrease,
    )


def peak_normalize_audio(y: np.ndarray) -> np.ndarray:
    """Peak-normalize an audio array without clipping.

    Args:
        y: Input audio array.

    Returns:
        Peak-normalized audio array. Returns the original array for silent input.
    """
    max_amp = np.max(np.abs(y))
    if max_amp > 0:
        return y / max_amp
    return y


def separate_vocals(
    audio_path: str,
    output_dir: str,
) -> str:
    """Separate vocals from a reference audio file when VR is enabled.

    Args:
        audio_path: Source WAV path.
        output_dir: Directory for separated vocal output.

    Returns:
        Separated vocal WAV path. Returns the original path when VR is disabled
        or separation fails.
    """

    if not config.VR_ENABLED:
        logger.info("VR disabled, skipping vocal separation")
        return audio_path

    try:
        from audio_separator.separator import Separator
    except ImportError:
        logger.warning(
            "audio-separator 패키지 미설치. "
            "pip install audio-separator[gpu] 로 설치하세요. "
            "원본 오디오를 사용합니다."
        )
        return audio_path

    # 디바이스 결정
    device = config.VR_DEVICE
    if device == "auto":
        import torch

        device = "cuda" if torch.cuda.is_available() else "cpu"

    try:
        separator_kwargs = {
            "model_file_dir": os.path.join(
                os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
                "models",
                "vr",
            ),
            "output_dir": output_dir,
        }

        separator = Separator(**separator_kwargs)
        separator.load_model(model_filename=config.VR_MODEL)

        output_files = separator.separate(audio_path)

        # audio-separator는 [vocals, accompaniment] 순서로 반환
        if output_files:
            print(output_files)
            vocal_path = os.path.join(output_dir, output_files[-1])
            if os.path.exists(vocal_path):
                logger.info("Vocal separation 완료: %s", vocal_path)
                return vocal_path

        logger.warning(
            "VR 출력 파일을 찾을 수 없습니다. 원본 오디오를 사용합니다."
        )
        return audio_path

    except Exception as exc:
        logger.warning("보컬 분리 실패 (fallback to original): %s", exc)
        return audio_path


def trim_boundary_fragments(
    word_timestamps: list[dict[str, Any]],
    full_text: str,
    audio_duration_sec: float,
    front_score_threshold: float = config.TRIM_FRONT_SCORE,
    back_score_threshold: float = config.TRIM_BACK_SCORE,
    boundary_gap_sec: float = config.TRIM_BOUNDARY_GAP,
    min_words: int = config.TRIM_MIN_WORDS,
) -> tuple[list[dict[str, Any]], str]:
    """Trim incomplete utterances near the request boundaries.

    Args:
        word_timestamps: WhisperX aligned word list.
        full_text: Full aligned transcript text.
        audio_duration_sec: Actual audio duration in seconds.
        front_score_threshold: Low-confidence threshold for the front boundary.
        back_score_threshold: Low-confidence threshold for the back boundary.
        boundary_gap_sec: Boundary gap threshold in seconds.
        min_words: Minimum word count required after trimming.

    Returns:
        Tuple of trimmed word timestamps and refined text.
    """
    if not word_timestamps:
        return [], ""

    trimmed = list(word_timestamps)

    start_idx = 0
    for i, word_info in enumerate(trimmed):
        word_clean = re.sub(r"[^a-zA-Z']", "", word_info["word"])
        is_lowercase = bool(word_clean) and word_clean[0].islower()
        is_low_conf = word_info.get("score", 1.0) < front_score_threshold
        if is_lowercase and is_low_conf:
            start_idx = i + 1
        else:
            break

    trimmed = trimmed[start_idx:]
    if not trimmed:
        return [], ""

    if trimmed and trimmed[0].get("start", 0.0) > max(0.35, boundary_gap_sec):
        extra_start_idx = 0
        for i, word_info in enumerate(trimmed):
            is_low_conf = word_info.get("score", 1.0) < front_score_threshold
            if not is_low_conf:
                break
            extra_start_idx = i + 1

        if extra_start_idx > 0 and len(trimmed[extra_start_idx:]) >= min_words:
            trimmed = trimmed[extra_start_idx:]
            if not trimmed:
                return [], ""

    sentence_end_re = re.compile(r"[.!?][\"']?$")

    last_complete_idx = len(trimmed) - 1

    for i in range(len(trimmed) - 1, -1, -1):
        word_info = trimmed[i]
        last_end = word_info.get("end", audio_duration_sec)
        at_boundary = (audio_duration_sec - last_end) < boundary_gap_sec
        low_conf = word_info.get("score", 1.0) < back_score_threshold
        no_punct = not sentence_end_re.search(word_info["word"].strip())

        if at_boundary and (low_conf or no_punct):
            for j in range(i - 1, -1, -1):
                if sentence_end_re.search(trimmed[j]["word"].strip()):
                    last_complete_idx = j
                    break
            else:
                last_complete_idx = -1
            break

    if last_complete_idx == -1:
        return [], ""

    trimmed = trimmed[: last_complete_idx + 1]

    if len(trimmed) < min_words:
        return [], ""

    refined_text = " ".join(word_info["word"] for word_info in trimmed).strip()
    return trimmed, refined_text
