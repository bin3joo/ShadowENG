"""split_into_sentences_with_timestamps 동작 검증 스크립트."""

from test.test_utils import run_and_save_output

from domain.processing.text_processing import (
    split_into_sentences_with_timestamps,
)


def _run() -> None:
    """문장 분리 검증 시나리오를 실행합니다."""
    full_text = (
        "I had this meeting with a big studio Hollywood casting director. "
        "And he looked at me and he said, everybody knows you're a beautiful, talented black girl, "
        "but what do I do? "
        "That was a turning point for me!"
    )

    word_timestamps = [
        {"word": "I", "start": 0.5, "end": 0.6},
        {"word": "had", "start": 0.6, "end": 0.8},
        {"word": "this", "start": 0.8, "end": 1.0},
        {"word": "meeting", "start": 1.0, "end": 1.4},
        {"word": "with", "start": 1.4, "end": 1.6},
        {"word": "a", "start": 1.6, "end": 1.7},
        {"word": "big", "start": 1.7, "end": 1.9},
        {"word": "studio", "start": 1.9, "end": 2.3},
        {"word": "Hollywood", "start": 2.3, "end": 2.8},
        {"word": "casting", "start": 2.8, "end": 3.1},
        {"word": "director.", "start": 3.1, "end": 3.6},
        {"word": "And", "start": 4.0, "end": 4.2},
        {"word": "he", "start": 4.2, "end": 4.3},
        {"word": "looked", "start": 4.3, "end": 4.6},
        {"word": "at", "start": 4.6, "end": 4.7},
        {"word": "me", "start": 4.7, "end": 4.9},
        {"word": "and", "start": 4.9, "end": 5.0},
        {"word": "he", "start": 5.0, "end": 5.1},
        {"word": "said,", "start": 5.1, "end": 5.4},
        {"word": "everybody", "start": 5.4, "end": 5.9},
        {"word": "knows", "start": 5.9, "end": 6.2},
        {"word": "you're", "start": 6.2, "end": 6.5},
        {"word": "a", "start": 6.5, "end": 6.6},
        {"word": "beautiful,", "start": 6.6, "end": 7.1},
        {"word": "talented", "start": 7.1, "end": 7.5},
        {"word": "black", "start": 7.5, "end": 7.8},
        {"word": "girl,", "start": 7.8, "end": 8.1},
        {"word": "but", "start": 8.1, "end": 8.3},
        {"word": "what", "start": 8.3, "end": 8.5},
        {"word": "do", "start": 8.5, "end": 8.6},
        {"word": "I", "start": 8.6, "end": 8.7},
        {"word": "do?", "start": 8.7, "end": 9.0},
        {"word": "That", "start": 9.5, "end": 9.7},
        {"word": "was", "start": 9.7, "end": 9.9},
        {"word": "a", "start": 9.9, "end": 10.0},
        {"word": "turning", "start": 10.0, "end": 10.4},
        {"word": "point", "start": 10.4, "end": 10.7},
        {"word": "for", "start": 10.7, "end": 10.8},
        {"word": "me!", "start": 10.8, "end": 11.1},
    ]

    results = split_into_sentences_with_timestamps(full_text, word_timestamps)

    print(f"총 {len(results)}개 문장으로 분리\n")
    for index, sentence in enumerate(results, 1):
        print(f"[문장 {index}]")
        print(f"  text      : {sentence['sentence']}")
        print(
            f"  time      : {sentence['start_sec']}s ~ {sentence['end_sec']}s  (duration: {sentence['duration_sec']}s)"
        )
        print(f"  WPM       : {sentence['wpm']}")
        print(f"  단어 수   : {sentence['word_count']}")
        print(
            f"  난이도    : {sentence['difficulty']} (score={sentence['difficulty_score']})"
        )
        print(f"  연음 패턴 : {sentence['reductions']}")
        print(f"  핵심 표현 : {sentence['key_expressions']}")
        print()

    print("=" * 50)
    print("연음 패턴 감지 테스트")
    print("=" * 50)
    texts_with_reductions = [
        "I'm going to give you everything.",
        "We're gonna want to know what you do.",
        "I've got to let me know.",
    ]

    for text in texts_with_reductions:
        words = [
            {"word": word, "start": index * 0.3, "end": index * 0.3 + 0.25}
            for index, word in enumerate(text.split())
        ]
        results = split_into_sentences_with_timestamps(text, words)
        if results:
            print(f"  text: {text}")
            print(f"  reductions: {results[0]['reductions']}")
            print()


def main() -> None:
    """검증 실행 결과를 result 폴더에 저장합니다."""
    output_path = run_and_save_output("sentence_verify.txt", _run)
    print(f"\n💾 검증 출력 저장: {output_path}")


if __name__ == "__main__":
    main()
