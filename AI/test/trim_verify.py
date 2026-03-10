"""trim_boundary_fragments 로직 직접 검증 스크립트."""

import re
from test.test_utils import run_and_save_output

import config

PASS = 0
FAIL = 0


def trim_boundary_fragments(
    word_timestamps: list[dict],
    full_text: str,
    audio_duration_sec: float,
    front_score_threshold: float = config.TRIM_FRONT_SCORE,
    back_score_threshold: float = config.TRIM_BACK_SCORE,
    boundary_gap_sec: float = config.TRIM_BOUNDARY_GAP,
    min_words: int = config.TRIM_MIN_WORDS,
) -> tuple[list[dict], str]:
    """경계 정제 로직을 독립적으로 재현합니다."""
    del full_text
    if not word_timestamps:
        return [], ""
    trimmed = list(word_timestamps)
    start_idx = 0
    for index, word in enumerate(trimmed):
        word_clean = re.sub(r"[^a-zA-Z']", "", word["word"])
        is_lowercase = bool(word_clean) and word_clean[0].islower()
        is_low_conf = word.get("score", 1.0) < front_score_threshold
        if is_lowercase and is_low_conf:
            start_idx = index + 1
        else:
            break

    trimmed = trimmed[start_idx:]
    if not trimmed:
        return [], ""

    if trimmed and trimmed[0].get("start", 0.0) > max(0.35, boundary_gap_sec):
        extra_start_idx = 0
        for index, word in enumerate(trimmed):
            is_low_conf = word.get("score", 1.0) < front_score_threshold
            if not is_low_conf:
                break
            extra_start_idx = index + 1

        if extra_start_idx > 0 and len(trimmed[extra_start_idx:]) >= min_words:
            trimmed = trimmed[extra_start_idx:]
            if not trimmed:
                return [], ""

    sentence_end_re = re.compile(r"[.!?][\"']?$")
    last_complete_idx = len(trimmed) - 1

    for index in range(len(trimmed) - 1, -1, -1):
        word = trimmed[index]
        last_end = word.get("end", audio_duration_sec)
        at_boundary = (audio_duration_sec - last_end) < boundary_gap_sec
        low_conf = word.get("score", 1.0) < back_score_threshold
        no_punct = not sentence_end_re.search(word["word"].strip())
        if at_boundary and (low_conf or no_punct):
            for inner_index in range(index - 1, -1, -1):
                if sentence_end_re.search(
                    trimmed[inner_index]["word"].strip()
                ):
                    last_complete_idx = inner_index
                    break
            else:
                last_complete_idx = -1
            break

    if last_complete_idx == -1:
        return [], ""
    trimmed = trimmed[: last_complete_idx + 1]
    if len(trimmed) < min_words:
        return [], ""
    refined_text = " ".join(word["word"] for word in trimmed).strip()
    return trimmed, refined_text


def check(label: str, got: str, expected: str) -> None:
    """검증 결과를 누적 출력합니다."""
    global PASS, FAIL
    if got == expected:
        print(f"  ✅ {label}")
        PASS += 1
    else:
        print(f"  ❌ {label}: got={got!r}, expected={expected!r}")
        FAIL += 1


def _run() -> None:
    """trim 경계 검증 케이스를 실행합니다."""
    w1 = [
        {"word": "beautiful,", "start": 0.1, "end": 0.6, "score": 0.43},
        {"word": "but", "start": 0.7, "end": 0.9, "score": 0.40},
        {"word": "What", "start": 1.0, "end": 1.2, "score": 0.85},
        {"word": "do", "start": 1.2, "end": 1.4, "score": 0.90},
        {"word": "you", "start": 1.4, "end": 1.6, "score": 0.91},
        {"word": "want?", "start": 1.6, "end": 2.0, "score": 0.88},
    ]
    _, text = trim_boundary_fragments(w1, "", 15.0)
    check("앞부분 잘린 발화 제거", text, "What do you want?")

    w2 = [
        {"word": "I", "start": 0.5, "end": 0.6, "score": 0.92},
        {"word": "had", "start": 0.6, "end": 0.8, "score": 0.90},
        {"word": "a", "start": 0.8, "end": 0.9, "score": 0.91},
        {"word": "meeting.", "start": 0.9, "end": 1.5, "score": 0.88},
        {"word": "And", "start": 2.0, "end": 2.2, "score": 0.87},
        {"word": "that", "start": 2.2, "end": 2.45, "score": 0.44},
    ]
    _, text = trim_boundary_fragments(w2, "", 2.55)
    check("뒷부분 잘린 발화 제거 (복합신호)", text, "I had a meeting.")

    w3 = [
        {"word": "She", "start": 0.0, "end": 0.3, "score": 0.95},
        {"word": "said", "start": 0.3, "end": 0.6, "score": 0.92},
        {"word": "hello.", "start": 0.6, "end": 1.0, "score": 0.91},
    ]
    _, text = trim_boundary_fragments(w3, "", 15.0)
    check("완전한 문장 (제거 없음)", text, "She said hello.")

    w4 = [
        {"word": "know", "start": 0.1, "end": 0.3, "score": 0.42},
        {"word": "That", "start": 0.5, "end": 0.7, "score": 0.90},
        {"word": "was", "start": 0.7, "end": 0.9, "score": 0.91},
        {"word": "perfect.", "start": 0.9, "end": 1.4, "score": 0.89},
        {"word": "But", "start": 2.0, "end": 2.25, "score": 0.44},
    ]
    _, text = trim_boundary_fragments(w4, "", 2.35)
    check("앞뒤 동시 잘린 발화", text, "That was perfect.")

    w5 = [
        {"word": "She", "start": 0.0, "end": 0.3, "score": 0.95},
        {"word": "said", "start": 0.3, "end": 0.6, "score": 0.92},
        {"word": "goodbye", "start": 0.6, "end": 1.0, "score": 0.90},
    ]
    _, text = trim_boundary_fragments(w5, "", 15.0)
    check("구두점 없어도 경계에서 멀면 제거 안됨", text, "She said goodbye")

    print(f"\n결과: {PASS} PASS, {FAIL} FAIL")


def main() -> None:
    """검증 실행 결과를 result 폴더에 저장합니다."""
    output_path = run_and_save_output("trim_verify.txt", _run)
    print(f"\n💾 검증 출력 저장: {output_path}")


if __name__ == "__main__":
    main()
