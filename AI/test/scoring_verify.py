"""StyleEcho 점수 측정 로직 검증 스크립트."""

import numpy as np

from test.test_utils import run_and_save_output

import config


def _run() -> None:
    """점수 계산 검증 출력을 실행합니다."""
    print("=" * 70)
    print("1. WORD ACCURACY (WER 기반)")
    print("=" * 70)
    test_wers = [0.0, 0.1, 0.5, 1.0, 1.5, 2.0]
    for wer in test_wers:
        score = 100.0 * np.exp(-2.5 * wer)
        print(f"  WER={wer:.1f} -> score={score:.1f}")

    print("\n" + "=" * 70)
    print("2. SPEED SIMILARITY (Asymmetric Ratio)")
    print("=" * 70)
    k = config.SPEED_K
    rushing_penalty = config.SPEED_RUSHING_PENALTY
    test_pairs = [
        (5.0, 5.0),
        (5.0, 10.0),
        (10.0, 5.0),
        (5.0, 6.0),
        (1.0, 10.0),
        (0.0, 5.0),
        (0.0, 0.0),
    ]
    for user_time, ref_time in test_pairs:
        if ref_time <= 0 and user_time <= 0:
            score = 100.0
        elif ref_time <= 0 or user_time <= 0:
            score = 0.0
        else:
            speed_ratio = user_time / ref_time
            if speed_ratio < 1.0:
                score = 100.0 * (speed_ratio ** (k * rushing_penalty))
            else:
                score = 100.0 * ((1.0 / speed_ratio) ** k)
        print(
            f"  user={user_time:.1f}s, ref={ref_time:.1f}s -> score={score:.1f}"
        )

    print("\n" + "=" * 70)
    print("3. PAUSE SIMILARITY (Gaussian)")
    print("=" * 70)
    sigma = config.PAUSE_SIGMA
    for diff in [0, 1, 2, 3, 5, 10]:
        score = 100.0 * np.exp(-(diff**2) / (2 * sigma**2))
        print(f"  |diff|={diff} -> score={score:.1f}")

    print("\n" + "=" * 70)
    print("4. PROSODY (DTW + exp decay)")
    print("=" * 70)
    beta = config.PROSODY_BETA
    for distance in [0.0, 0.1, 0.5, 1.0, 2.0, 3.0, 5.0]:
        score = 100.0 * np.exp(-beta * distance)
        print(f"  norm_dist={distance:.1f} -> score={score:.1f}")

    print("\n" + "=" * 70)
    print("5. BOUNDARY TONE (slope)")
    print("=" * 70)
    slope_threshold = config.BOUNDARY_SLOPE_THRESHOLD
    test_slopes = [
        (100.0, 120.0, "similar rise"),
        (100.0, -50.0, "opposite"),
        (-100.0, -120.0, "similar fall"),
        (100.0, 10.0, "user weak rise"),
        (0.0, 0.0, "both flat"),
        (100.0, 100.0, "perfect"),
        (0.001, 100.0, "ref almost flat"),
    ]
    for ref_slope, user_slope, description in test_slopes:
        if (
            abs(ref_slope) < slope_threshold
            and abs(user_slope) < slope_threshold
        ):
            score = 100.0
            status = "good"
        elif abs(ref_slope) < slope_threshold:
            score = max(
                60.0,
                100.0 - (abs(user_slope) / (slope_threshold * 2)) * 40.0,
            )
            status = "weak"
        elif (ref_slope * user_slope) < 0:
            score = 40.0
            status = "opposite"
        else:
            ref_mag = abs(ref_slope)
            user_mag = abs(user_slope)
            max_mag = max(ref_mag, user_mag)
            score = (
                100.0
                if max_mag == 0
                else 100.0 * ((min(ref_mag, user_mag) / max_mag) ** 0.8)
            )
            status = "good" if score > 80 else "weak"
        print(
            f"  ref={ref_slope:>8.1f}, user={user_slope:>8.1f} ({description:>20s}) -> score={score:.1f} [{status}]"
        )

    print("\n" + "=" * 70)
    print("6. DYNAMIC STRESS (CV)")
    print("=" * 70)
    test_cvs = [
        (0.3, 0.3, "perfect"),
        (0.3, 0.1, "monotone"),
        (0.3, 0.5, "exaggerated"),
        (0.3, 0.21, "0.7 boundary"),
        (0.3, 0.39, "1.3 boundary"),
        (0.01, 0.5, "extreme"),
    ]
    for ref_cv, user_cv, description in test_cvs:
        max_cv = max(ref_cv, user_cv)
        score = (
            100.0
            if max_cv == 0
            else 100.0 * ((min(ref_cv, user_cv) / max_cv) ** 1.2)
        )
        if score >= 80.0:
            status = "good"
        elif user_cv < ref_cv:
            status = "monotone"
        else:
            status = "exaggerated"
        print(
            f"  ref_cv={ref_cv:.2f}, user_cv={user_cv:.2f} ({description:>15s}) -> score={score:.1f} [{status}]"
        )

    print("\n" + "=" * 70)
    print("7. WORD RHYTHM")
    print("=" * 70)
    print("  7a. diff_ratio asymmetry:")
    pairs = [(0.10, 0.05), (0.05, 0.10)]
    for ref_rd, user_rd in pairs:
        max_rd = max(ref_rd, user_rd)
        word_score = (
            1.0
            if max_rd == 0
            else (min(ref_rd, user_rd) / max_rd) ** config.RHYTHM_K
        )
        diff_ratio = abs(ref_rd - user_rd) / ((ref_rd + user_rd) / 2 + 1e-8)
        status = "good"
        if diff_ratio > config.RHYTHM_DIFF_THRESHOLD:
            status = "dragged" if user_rd > ref_rd else "rushed"
        print(
            f"  r_rd={ref_rd:.2f}, u_rd={user_rd:.2f} -> word_score={word_score:.3f}, diff_ratio={diff_ratio:.2f}, status={status}"
        )

    print("\n  7b. denominator mismatch:")
    print("  rhythm_score = sum(word_scores) / len(word_scores) * 100")
    print(
        "  현재 구현은 punctuation-only 단어를 건너뛴 뒤 실제 매칭된 word_scores 수로 평균을 냅니다."
    )
    print("  Example: ref_words=10, skip 3 punctuation, evaluate 7:")
    example_scores = [0.9, 0.8, 0.95, 0.7, 0.85, 0.9, 0.88]
    score_by_ref = 100.0 * sum(example_scores) / 10
    score_by_actual = 100.0 * sum(example_scores) / 7
    print(f"  Divided by len(ref_words)=10: {score_by_ref:.1f}")
    print(f"  Divided by actual_evaluated=7: {score_by_actual:.1f}")
    print(f"  Difference: {score_by_actual - score_by_ref:.1f} points!")

    print("\n" + "=" * 70)
    print("8. WEIGHT SUM")
    print("=" * 70)
    scoring_weights = config.SCORE_WEIGHTS
    weights = {
        "word": scoring_weights.get("word_accuracy", 0.30),
        "prosody": scoring_weights.get("prosody", 0.20),
        "rhythm": scoring_weights.get("rhythm", 0.15),
        "boundary": scoring_weights.get("boundary_tone", 0.10),
        "dynamic": scoring_weights.get("dynamic_stress", 0.10),
        "speed": scoring_weights.get("speed", 0.075),
        "pause": scoring_weights.get("pause", 0.075),
    }
    print(f"  Sum = {sum(weights.values())}")
    all_100 = sum(100.0 * weight for weight in weights.values())
    print(f"  All 100 -> total = {all_100:.1f}")

    print("\n" + "=" * 70)
    print("9. UX SCENARIOS")
    print("=" * 70)
    scenario_a = {
        "word": 100,
        "prosody": 30,
        "rhythm": 90,
        "boundary": 80,
        "dynamic": 40,
        "speed": 95,
        "pause": 100,
    }
    total_a = sum(scenario_a[key] * weights[key] for key in weights)
    print(f"  A (perfect text, bad prosody): {total_a:.1f}")

    scenario_b = {
        "word": 50,
        "prosody": 95,
        "rhythm": 60,
        "boundary": 95,
        "dynamic": 90,
        "speed": 90,
        "pause": 90,
    }
    total_b = sum(scenario_b[key] * weights[key] for key in weights)
    print(f"  B (half words wrong, perfect prosody): {total_b:.1f}")

    print(f"\n  A={total_a:.1f} vs B={total_b:.1f}")


def main() -> None:
    """검증 실행 결과를 result 폴더에 저장합니다."""
    output_path = run_and_save_output("scoring_verify.txt", _run)
    print(f"\n💾 검증 출력 저장: {output_path}")


if __name__ == "__main__":
    main()
