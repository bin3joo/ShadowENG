"""단어별 미시 리듬 분석."""

from typing import Any

import config
from domain.processing.engine_utils import _normalize_word


def analyze_word_rhythm(
    ref_words: list[dict[str, Any]],
    user_words: list[dict[str, Any]],
    ref_active_time: float,
    user_active_time: float,
) -> tuple[float, list[dict[str, Any]]]:
    """단어별 상대적 길이(RD)를 비교하여 리듬 점수를 산출합니다.

    Args:
        ref_words: 레퍼런스 단어 타임스탬프 리스트.
        user_words: 유저 단어 타임스탬프 리스트.
        ref_active_time: 레퍼런스 활성 발화 시간(초).
        user_active_time: 유저 활성 발화 시간(초).

    Returns:
        ``(rhythm_score, word_feedback)`` 튜플.
    """
    if not ref_words or ref_active_time <= 0:
        return 100.0, []

    if not user_words or user_active_time <= 0:
        word_feedback = []
        for r_word in ref_words:
            word_feedback.append(
                {
                    "word": r_word.get("word", ""),
                    "status": "missed",
                    "ref_start_time": round(r_word.get("start", 0.0), 2),
                    "ref_end_time": round(r_word.get("end", 0.0), 2),
                    "user_start_time": None,
                    "user_end_time": None,
                }
            )
        return 0.0, word_feedback

    word_scores: list[float] = []
    word_feedback: list[dict] = []

    # dict 기반 O(n) 매칭용 인덱스 구축
    _user_word_index: dict[str, list[int]] = {}
    for _ui, _uw in enumerate(user_words):
        _key = _normalize_word(_uw["word"])
        if _key:
            _user_word_index.setdefault(_key, []).append(_ui)
    _used_indices: set[int] = set()

    k = config.RHYTHM_K

    for r_word in ref_words:
        r_text = _normalize_word(r_word["word"])
        if not r_text:
            continue

        matched_idx = -1
        candidates = _user_word_index.get(r_text, [])
        for ci in candidates:
            if ci not in _used_indices:
                matched_idx = ci
                break

        if matched_idx != -1:
            _used_indices.add(matched_idx)
            u_word = user_words[matched_idx]

            r_dur = r_word["end"] - r_word["start"]
            u_dur = u_word["end"] - u_word["start"]

            r_rd = r_dur / ref_active_time
            u_rd = u_dur / user_active_time

            max_rd = max(r_rd, u_rd)
            word_score = (
                1.0 if max_rd == 0 else (min(r_rd, u_rd) / max_rd) ** k
            )

            word_scores.append(word_score)

            diff_ratio = abs(r_rd - u_rd) / ((r_rd + u_rd) / 2 + 1e-8)
            status = "good"
            if diff_ratio > config.RHYTHM_DIFF_THRESHOLD:
                status = "dragged" if u_rd > r_rd else "rushed"

            word_feedback.append(
                {
                    "word": r_word["word"],
                    "status": status,
                    "ref_start_time": round(r_word["start"], 2),
                    "ref_end_time": round(r_word["end"], 2),
                    "ref_duration_sec": round(r_dur, 2),
                    "user_start_time": round(u_word["start"], 2),
                    "user_end_time": round(u_word["end"], 2),
                    "user_duration_sec": round(u_dur, 2),
                }
            )
        else:
            word_feedback.append(
                {
                    "word": r_word["word"],
                    "status": "missed",
                    "ref_start_time": round(r_word["start"], 2),
                    "ref_end_time": round(r_word["end"], 2),
                    "user_start_time": None,
                    "user_end_time": None,
                }
            )

    rhythm_score = (
        100.0 * (sum(word_scores) / len(word_scores))
        if word_scores
        else 0.0
    )
    return round(rhythm_score, 1), word_feedback
