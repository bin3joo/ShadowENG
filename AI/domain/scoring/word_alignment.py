"""유저 단어를 레퍼런스 구조에 맞게 정렬."""

from typing import Any

from domain.processing.engine_utils import _canonicalize_tokens


def align_user_words_to_ref(
    ref_words: list[dict[str, Any]],
    user_words: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """레퍼런스 단어 구조에 맞춰 유저 단어를 병합합니다.

    레퍼런스에 뭉친 단어가 있으면 유저의 분리된 단어들을
    하나로 병합하여 양쪽 구조를 통일합니다.

    Args:
        ref_words: 레퍼런스 단어 타임스탬프 리스트.
        user_words: 유저 단어 타임스탬프 리스트.

    Returns:
        레퍼런스 구조에 정렬된 유저 단어 리스트.
    """
    aligned: list[dict] = []
    available = list(user_words)

    def _build_canonical_span(
        words: list[dict],
    ) -> tuple[list[str], dict] | None:
        canonical_tokens: list[str] = []
        matched_words: list[dict] = []

        for word in words:
            tokens = _canonicalize_tokens(word.get("word", ""))
            if not tokens:
                continue
            canonical_tokens.extend(tokens)
            matched_words.append(word)

        if not canonical_tokens or not matched_words:
            return None

        merged_entry = {
            "word": " ".join(
                word.get("word", "") for word in matched_words
            ),
            "start": matched_words[0]["start"],
            "end": matched_words[-1]["end"],
            "score": sum(word.get("score", 0.0) for word in matched_words)
            / len(matched_words),
        }
        return canonical_tokens, merged_entry

    for r_word in ref_words:
        ref_tokens = _canonicalize_tokens(r_word.get("word", ""))
        if not ref_tokens:
            continue

        match_found = False
        for start_idx in range(len(available)):
            candidate_words: list[dict] = []
            candidate_tokens: list[str] = []

            for end_idx in range(start_idx, len(available)):
                candidate_words.append(available[end_idx])
                canonical_span = _build_canonical_span(candidate_words)
                if canonical_span is None:
                    continue

                candidate_tokens, merged_entry = canonical_span
                if len(candidate_tokens) > len(ref_tokens):
                    break
                if candidate_tokens == ref_tokens:
                    indices = list(range(start_idx, end_idx + 1))
                    aligned.append(merged_entry)
                    for idx in sorted(indices, reverse=True):
                        available.pop(idx)
                    match_found = True
                    break

            if match_found:
                break

    aligned.extend(available)
    return aligned
