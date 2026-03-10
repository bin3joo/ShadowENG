"""
StyleEcho 오디오 처리
=====================
분석용 디노이징(Track B)과 구간 경계 정제(trim) 로직을 담당합니다.
"""

import re

import noisereduce as nr
import numpy as np

try:
    from ... import config
except ImportError:
    import config


def denoise_for_analysis(
    y: np.ndarray,
    sr: int,
    profile: str | None = None,
) -> np.ndarray:
    """
    librosa 억양/강세 분석 전용 노이즈 제거 (Track B).
    WhisperX STT 에는 절대 사용하지 않습니다.

    noisereduce Spectral Gating:
    - stationary=True  : 백색소음/에어콘/팬 소음 등 정상(stationary) 노이즈에 최적
    - prop_decrease=0.8: 노이즈 80% 제거 (과제거 시 음성 왜곡 방지)
    - 처리 시간 ~0.05초 (CPU 기준)
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


def trim_boundary_fragments(
    word_timestamps: list[dict],
    full_text: str,
    audio_duration_sec: float,
    front_score_threshold: float = config.TRIM_FRONT_SCORE,
    back_score_threshold: float = config.TRIM_BACK_SCORE,
    boundary_gap_sec: float = config.TRIM_BOUNDARY_GAP,
    min_words: int = config.TRIM_MIN_WORDS,
) -> tuple[list[dict], str]:
    """
    YouTube 레퍼런스 선택 구간 앞뒤의 불완전 발화를 제거합니다.

    Parameters
    ----------
    word_timestamps    : WhisperX Forced Alignment 단어 목록
    full_text          : WhisperX 전사 텍스트
    audio_duration_sec : librosa.get_duration() 등으로 구한 파일 실제 길이(초)
    front_score_threshold : 앞부분 alignment score 임계치 (< 이면 저신뢰)
    back_score_threshold  : 뒤부분 alignment score 임계치
    boundary_gap_sec      : 마지막 단어 end vs 오디오 끝의 거리 임계치 (초)
    min_words             : 정제 후 최소 단어 수 (미달이면 빈 반환)

    Returns
    -------
    (refined_word_timestamps, refined_text)
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
