"""종결 억양 분석 (문장 끝 억양 방향 비교)."""

from typing import Any

import config
import numpy as np


def analyze_boundary_tone(
    ref_f0: np.ndarray, user_f0: np.ndarray
) -> tuple[float, dict[str, Any]]:
    """문장 끝부분의 억양 기울기(Slope)를 비교합니다.

    - 마지막 15% 또는 최소 ``BOUNDARY_TAIL_MIN_MS`` ms 중 긴 쪽을 사용
    - F0 에 이동평균(window=3) 평활화를 적용하여 노이즈 왜곡 방지
    - Soft Ratio bias 로 평음 근처에서의 점수 폭락 방지
    - 부호가 반대여도 양쪽 모두 평음이면 완화 점수 부여

    Args:
        ref_f0: 레퍼런스 F0 배열.
        user_f0: 유저 F0 배열.

    Returns:
        ``(score, details)`` 튜플.

        - **score**: 0 ~ 100 점수.
        - **details**: ``ref_slope``, ``user_slope``, ``status`` 키를 포함.
    """
    r_valid = ref_f0[ref_f0 > 0]
    u_valid = user_f0[user_f0 > 0]

    # P0 수정: 짧을 때도 dict 반환
    if len(r_valid) < 10 or len(u_valid) < 10:
        return 100.0, {
            "ref_slope": 0.0,
            "user_slope": 0.0,
            "status": "short",
        }

    # ── 이동평균 평활화 (window=3) ──
    def _smooth(arr: np.ndarray, w: int = 3) -> np.ndarray:
        if len(arr) < w:
            return arr
        kernel = np.ones(w) / w
        return np.convolve(arr, kernel, mode="valid")

    r_valid = _smooth(r_valid)
    u_valid = _smooth(u_valid)

    # ── 꼬리 구간 추출: max(마지막 15%, 최소 tail_min_frames) ──
    sr_est = config.TARGET_SR
    hop = config.HOP_LENGTH
    tail_min_frames = max(
        3, int(config.BOUNDARY_TAIL_MIN_MS / 1000.0 * sr_est / hop)
    )

    r_tail_len = max(int(len(r_valid) * 0.15), tail_min_frames)
    u_tail_len = max(int(len(u_valid) * 0.15), tail_min_frames)
    r_tail = r_valid[-min(r_tail_len, len(r_valid)) :]
    u_tail = u_valid[-min(u_tail_len, len(u_valid)) :]

    r_x = np.linspace(0, 1, len(r_tail))
    u_x = np.linspace(0, 1, len(u_tail))

    r_tail_st = 12.0 * np.log2(np.maximum(r_tail, 1e-8) / 55.0)
    u_tail_st = 12.0 * np.log2(np.maximum(u_tail, 1e-8) / 55.0)

    r_m, _ = np.polyfit(r_x, r_tail_st, 1)
    u_m, _ = np.polyfit(u_x, u_tail_st, 1)

    SLOPE_THRESHOLD = config.BOUNDARY_SLOPE_THRESHOLD
    bias = config.BOUNDARY_SLOPE_BIAS

    # ── 양쪽 모두 평음(Dead Zone) ──
    if abs(r_m) < SLOPE_THRESHOLD and abs(u_m) < SLOPE_THRESHOLD:
        score = 100.0
        status = "good"
    # ── 레퍼런스만 평음 ──
    elif abs(r_m) < SLOPE_THRESHOLD:
        score = max(
            60.0,
            100.0 - (abs(u_m) / (SLOPE_THRESHOLD * 2)) * 40.0,
        )
        status = "weak"
    # ── 부호 반대 ──
    elif (r_m * u_m) < 0:
        # 양쪽 모두 작은 기울기(평음 근처)이면 완화
        if (
            abs(r_m) < SLOPE_THRESHOLD * 2
            and abs(u_m) < SLOPE_THRESHOLD * 2
        ):
            score = config.BOUNDARY_OPPOSITE_SOFT_SCORE
        else:
            score = config.BOUNDARY_OPPOSITE_SCORE
        status = "opposite"
    # ── 같은 방향: Soft Ratio 적용 ──
    else:
        r_mag, u_mag = abs(r_m), abs(u_m)
        max_mag = max(r_mag, u_mag)
        score = (
            100.0
            if max_mag == 0
            else 100.0
            * ((min(r_mag, u_mag) + bias) / (max_mag + bias))
            ** config.BOUNDARY_K
        )
        status = (
            "good" if score > config.BOUNDARY_GOOD_THRESHOLD else "weak"
        )

    return round(float(score), 1), {
        "ref_slope": round(float(r_m), 1),
        "user_slope": round(float(u_m), 1),
        "status": status,
    }
