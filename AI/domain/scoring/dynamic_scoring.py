"""동적 강세 분석 (볼륨 역동성 비교)."""

from typing import Any

import config
import numpy as np


def analyze_dynamic_stress(
    ref_rms: np.ndarray, user_rms: np.ndarray
) -> tuple[float, dict[str, Any]]:
    """음성 에너지(RMS)의 변동 계수(CV)를 비교하여 역동성 점수를 산출합니다.

    Args:
        ref_rms: 레퍼런스 RMS 배열.
        user_rms: 유저 RMS 배열.

    Returns:
        ``(score, details)`` 튜플.

        - **score**: 0 ~ 100 점수.
        - **details**: ``ref_dynamic_ratio``, ``user_dynamic_ratio``,
          ``status`` 키를 포함.
    """
    r_mean = np.mean(ref_rms)
    u_mean = np.mean(user_rms)

    if r_mean == 0 or u_mean == 0:
        return 100.0, {
            "ref_dynamic_ratio": 0.0,
            "user_dynamic_ratio": 0.0,
            "status": "flat",
        }

    r_cv = float(np.std(ref_rms) / r_mean)
    u_cv = float(np.std(user_rms) / u_mean)

    max_cv = max(r_cv, u_cv)
    score = (
        100.0
        if max_cv == 0
        else 100.0 * ((min(r_cv, u_cv) / max_cv) ** config.DYNAMIC_K)
    )

    GOOD_SCORE_THRESHOLD = config.DYNAMIC_GOOD_THRESHOLD
    if score >= GOOD_SCORE_THRESHOLD:
        status = "good"
    elif u_cv < r_cv:
        status = "monotone"
    else:
        status = "exaggerated"

    return round(float(score), 1), {
        "ref_dynamic_ratio": round(r_cv, 2),
        "user_dynamic_ratio": round(u_cv, 2),
        "status": status,
    }
