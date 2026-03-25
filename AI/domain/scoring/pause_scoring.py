"""위치 기반 멈춤 정합 분석 (Alignment-Based Pause Scoring)."""

from typing import Any

from domain.processing.engine_utils import extract_pause_positions


def analyze_pause_alignment(
    ref_words: list[dict[str, Any]],
    aligned_user_words: list[dict[str, Any]],
) -> tuple[float, dict[str, Any]]:
    """레퍼런스와 유저의 휴지기(Pause) 위치를 1:1로 비교합니다.

    Args:
        ref_words: 레퍼런스 단어 타임스탬프.
        aligned_user_words: 정렬된 유저 단어 타임스탬프.

    Returns:
        ``(f1_score, details)`` 튜플.

        - **f1_score**: 0.0 ~ 1.0 사이의 F1 점수.
        - **details**: ``true_hits``, ``false_alarms``, ``misses``,
          ``precision``, ``recall`` 키를 포함.
    """
    ref_pauses = extract_pause_positions(ref_words)
    user_pauses = extract_pause_positions(aligned_user_words)

    # 레퍼런스에 쉼이 아예 없는 경우
    if not ref_pauses:
        # 유저도 안 쉬면 만점, 쉬면 감점
        if not user_pauses:
            return 1.0, {
                "true_hits": 0,
                "false_alarms": 0,
                "misses": 0,
                "precision": 1.0,
                "recall": 1.0,
            }
        return 0.0, {
            "true_hits": 0,
            "false_alarms": len(user_pauses),
            "misses": 0,
            "precision": 0.0,
            "recall": 1.0,
        }

    true_hits = len(ref_pauses & user_pauses)
    false_alarms = len(user_pauses - ref_pauses)
    misses = len(ref_pauses - user_pauses)

    precision = (
        true_hits / (true_hits + false_alarms)
        if (true_hits + false_alarms) > 0
        else 0.0
    )
    recall = (
        true_hits / (true_hits + misses)
        if (true_hits + misses) > 0
        else 0.0
    )
    f1 = (
        2 * precision * recall / (precision + recall)
        if (precision + recall) > 0
        else 0.0
    )

    return f1, {
        "true_hits": true_hits,
        "false_alarms": false_alarms,
        "misses": misses,
        "precision": round(precision, 3),
        "recall": round(recall, 3),
    }
