"""리듬 계산 보정 검증 스크립트."""

from test.test_utils import run_and_save_output

import numpy as np


def _run() -> None:
    """리듬 계산 보정값을 출력합니다."""
    print("=== FIX 1: diff_ratio symmetry ===")
    k = 1.2
    pairs = [(0.10, 0.05), (0.05, 0.10)]
    for ref_rd, user_rd in pairs:
        max_rd = max(ref_rd, user_rd)
        word_score = (
            1.0 if max_rd == 0 else (min(ref_rd, user_rd) / max_rd) ** k
        )
        old_diff = abs(ref_rd - user_rd) / (ref_rd + 1e-8)
        new_diff = abs(ref_rd - user_rd) / ((ref_rd + user_rd) / 2 + 1e-8)
        old_status = (
            "dragged"
            if user_rd > ref_rd
            else ("rushed" if old_diff > 0.4 else "good")
        )
        new_status = (
            "dragged"
            if user_rd > ref_rd
            else ("rushed" if new_diff > 0.4 else "good")
        )
        print(
            f"  r={ref_rd:.2f} u={user_rd:.2f} | "
            f"OLD diff={old_diff:.2f}({old_status})  "
            f"NEW diff={new_diff:.2f}({new_status})  "
            f"score={word_score:.3f}"
        )

    print()
    print("=== FIX 2: rhythm_score denominator ===")
    scores = [0.9, 0.8, 0.95, 0.7, 0.85, 0.9, 0.88]
    old_score = 100.0 * sum(scores) / 10
    new_score = 100.0 * sum(scores) / len(scores)
    print(f"  OLD (len(ref_words)=10): {old_score:.1f}")
    print(f"  NEW (len(word_scores)=7): {new_score:.1f}")
    print(f"  Corrected by +{new_score - old_score:.1f} points")

    print()
    print("=== FIX 3: numpy sanity ===")
    print(f"  mean(scores)={np.mean(scores):.3f}")


def main() -> None:
    """검증 실행 결과를 result 폴더에 저장합니다."""
    output_path = run_and_save_output("fix_verify.txt", _run)
    print(f"\n💾 검증 출력 저장: {output_path}")


if __name__ == "__main__":
    main()
