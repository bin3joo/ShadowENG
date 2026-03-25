"""단어별 피치 컨투어 피드백 (단어별 F0 높낮이 비교)."""

from typing import Any

import config
import numpy as np
from domain.processing.engine_utils import _normalize_word


def analyze_word_pitch_contour(
    ref_f0: np.ndarray,
    user_f0: np.ndarray,
    ref_words: list[dict[str, Any]],
    user_words: list[dict[str, Any]],
    sr: int = 16000,
    hop_length: int = 256,
    ref_speech_start: float = 0.0,
    user_speech_start: float = 0.0,
) -> list[dict[str, Any]]:
    """각 단어 구간의 F0 시작/종료 방향을 비교하여 피치 피드백을 생성합니다.

    Args:
        ref_f0: 레퍼런스 F0 배열.
        user_f0: 유저 F0 배열.
        ref_words: 레퍼런스 단어 타임스탬프.
        user_words: 유저 단어 타임스탬프.
        sr: 샘플레이트.
        hop_length: 프레임 홉 길이.
        ref_speech_start: 레퍼런스 발화 시작 오프셋(초).
        user_speech_start: 유저 발화 시작 오프셋(초).

    Returns:
        단어별 피치 컨투어 피드백 리스트.
    """

    def _direction(f0_arr, start_sec, end_sec, speech_start):
        rel_start = max(0.0, start_sec - speech_start)
        rel_end = max(0.0, end_sec - speech_start)

        s_frame = int(rel_start * sr / hop_length)
        e_frame = int(rel_end * sr / hop_length)
        if s_frame >= len(f0_arr) or e_frame <= s_frame:
            return "flat", 0.0, 0.0
        segment = f0_arr[s_frame:e_frame]
        valid = segment[segment > 0]

        if len(valid) == 0:
            return "flat", 0.0, 0.0
        elif len(valid) == 1:
            val = float(valid[0])
            return "flat", val, val

        # 비율 기반 flat 판정: diff / mean_f0 < threshold_ratio
        threshold_ratio = config.PITCH_FLAT_THRESHOLD_RATIO

        if len(valid) < 4:
            first_half = float(valid[0])
            second_half = float(valid[-1])
            diff = second_half - first_half
            mean_f0 = float(np.mean(valid))
            if mean_f0 > 0 and abs(diff) / mean_f0 < threshold_ratio:
                return "flat", first_half, second_half
            return (
                ("rising" if diff > 0 else "falling"),
                first_half,
                second_half,
            )

        mid = len(valid) // 2
        first_half = float(np.mean(valid[:mid]))
        second_half = float(np.mean(valid[mid:]))
        diff = second_half - first_half
        mean_f0 = float(np.mean(valid))
        if mean_f0 > 0 and abs(diff) / mean_f0 < threshold_ratio:
            return "flat", first_half, second_half
        return (
            ("rising" if diff > 0 else "falling"),
            first_half,
            second_half,
        )

    pitch_feedback: list[dict] = []

    # dict 기반 O(n) 매칭용 인덱스 구축
    _pitch_user_index: dict[str, list[int]] = {}
    for _pi, _pw in enumerate(user_words):
        _pkey = _normalize_word(_pw.get("word", ""))
        if _pkey:
            _pitch_user_index.setdefault(_pkey, []).append(_pi)
    _pitch_used: set[int] = set()

    for r_word in ref_words:
        r_text_clean = _normalize_word(r_word.get("word", ""))
        if not r_text_clean:
            continue

        matched_u = None
        for _ci in _pitch_user_index.get(r_text_clean, []):
            if _ci not in _pitch_used:
                _pitch_used.add(_ci)
                matched_u = user_words[_ci]
                break

        if matched_u is None:
            continue

        r_dir, r_first, r_second = _direction(
            ref_f0,
            r_word["start"],
            r_word["end"],
            ref_speech_start,
        )
        u_dir, u_first, u_second = _direction(
            user_f0,
            matched_u["start"],
            matched_u["end"],
            user_speech_start,
        )

        feedback = "good"
        if r_dir != u_dir and r_dir != "flat":
            if r_dir == "rising" and u_dir != "rising":
                feedback = "raise_end"
            elif r_dir == "falling" and u_dir != "falling":
                feedback = "lower_end"
        elif r_dir == u_dir and r_dir != "flat":
            r_delta = abs(r_second - r_first)
            u_delta = abs(u_second - u_first)
            if r_delta > 0 and u_delta / (r_delta + 1e-8) < 0.5:
                feedback = "more_emphasis"

        pitch_feedback.append(
            {
                "word": r_word["word"],
                "ref_direction": r_dir,
                "ref_start_hz": round(r_first, 1),
                "ref_end_hz": round(r_second, 1),
                "user_direction": u_dir,
                "user_start_hz": round(u_first, 1),
                "user_end_hz": round(u_second, 1),
                "feedback": feedback,
            }
        )

    return pitch_feedback
