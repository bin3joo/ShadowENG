"""
StyleEcho 텍스트 처리
=====================
문장/턴 분할, 레퍼런스 파트 빌드, 짧은 파트 병합 등
텍스트 기반 세그멘테이션 로직을 담당합니다.
"""

import re

try:
    from ... import config
    from .constants import A1_WORDS, REDUCTION_PATTERNS
    from .engine_utils import _CLEAN_WORD_RE, _count_word_tokens
    from .speaker_analysis import (
        _get_word_speaker_label,
        _parts_have_compatible_speakers,
        _speaker_run_words,
        smooth_word_speaker_labels,
    )
except ImportError:
    import config
    from domain.processing.constants import A1_WORDS, REDUCTION_PATTERNS
    from domain.processing.engine_utils import (
        _CLEAN_WORD_RE,
        _count_word_tokens,
    )
    from domain.processing.speaker_analysis import (
        _get_word_speaker_label,
        _parts_have_compatible_speakers,
        _speaker_run_words,
        smooth_word_speaker_labels,
    )


def _first_sentence_has_terminal_punctuation(
    words: list[dict],
    max_words: int,
) -> bool:
    """초반 단어 중 문장 종료 구두점이 포함되어 있는지 확인합니다."""
    for word in words[:max_words]:
        token = str(word.get("word", "")).strip()
        if token.endswith((".", "?", "!")):
            return True
    return False


def _has_terminal_punctuation(sentence: str) -> bool:
    """문장이 문장 종료 구두점으로 끝나는지 반환합니다."""
    return sentence.rstrip().endswith((".", "?", "!"))


def _words_duration_sec(words: list[dict]) -> float:
    """단어 리스트의 총 구간 길이를 계산합니다."""
    if not words:
        return 0.0
    start = words[0].get("start")
    end = words[-1].get("end")
    if start is None or end is None:
        return 0.0
    return max(0.0, float(end) - float(start))


def _words_meet_turn_evidence(
    words: list[dict],
    min_words: int = config.REFERENCE_SPEAKER_TURN_MIN_WORDS,
    min_duration_sec: float = config.REFERENCE_SPEAKER_TURN_MIN_DURATION_SEC,
) -> bool:
    """화자 기반 turn 경계로 인정할 최소 증거량을 판단합니다."""
    if not words:
        return False

    word_count = sum(_count_word_tokens(word) for word in words)
    duration_sec = _words_duration_sec(words)
    return word_count >= min_words or duration_sec >= min_duration_sec


def _build_reference_part(
    sentence: str,
    sentence_words: list[dict],
    target_words: list[str],
    part_source: str = "sentence",
    turn_id: int | None = None,
    turn_break_reason: str | None = None,
) -> dict | None:
    """문장/턴 기준 레퍼런스 파트 메타데이터를 계산합니다."""
    starts = [w["start"] for w in sentence_words if "start" in w]
    ends = [w["end"] for w in sentence_words if "end" in w]
    if not starts or not ends:
        return None

    sent_start = round(min(starts), 2)
    sent_end = round(max(ends), 2)
    duration_sec = round(sent_end - sent_start, 2)
    words_to_collect = len(target_words)
    wpm = (
        round((words_to_collect / duration_sec) * 60.0, 1)
        if duration_sec > 0
        else 0.0
    )

    sent_lower = sentence.lower()
    reductions: list[dict] = []
    for original, reduced in REDUCTION_PATTERNS.items():
        if original in sent_lower:
            reductions.append({"original": original, "reduced": reduced})

    key_expressions: list[str] = []
    seen: set[str] = set()
    for raw_word in target_words:
        clean = _CLEAN_WORD_RE.sub("", raw_word).lower()
        if clean and clean not in A1_WORDS and clean not in seen:
            key_expressions.append(raw_word)
            seen.add(clean)

    score = 0.0
    wpm_score = max(0.0, min(3.0, (wpm - 120) / 40))
    score += wpm_score
    len_score = max(0.0, min(2.0, (words_to_collect - 5) / 7.5))
    score += len_score
    vocab_ratio = len(key_expressions) / max(1, words_to_collect)
    score += vocab_ratio
    reduction_score = min(2.0, len(reductions) * 0.5)
    score += reduction_score
    difficulty_score = round(score, 1)

    if difficulty_score < 2.5:
        difficulty = "Easy"
    elif difficulty_score < 4.5:
        difficulty = "Normal"
    elif difficulty_score < 6.5:
        difficulty = "Hard"
    else:
        difficulty = "Expert"

    return {
        "sentence": sentence,
        "start_sec": sent_start,
        "end_sec": sent_end,
        "duration_sec": duration_sec,
        "word_count": words_to_collect,
        "wpm": wpm,
        "difficulty_score": difficulty_score,
        "difficulty": difficulty,
        "reductions": reductions,
        "key_expressions": key_expressions,
        "word_timestamps": sentence_words,
        "part_source": part_source,
        "turn_id": turn_id,
        "turn_break_reason": turn_break_reason,
        "speaker_risk": "low",
    }


def _should_merge_short_reference_part(
    part: dict,
    min_duration_sec: float,
) -> bool:
    """짧은 part 가 병합 대상인 파편인지 판단합니다."""
    duration_sec = float(part.get("duration_sec", 0.0))
    if duration_sec >= min_duration_sec:
        return False

    sentence = str(part.get("sentence", "")).strip()
    word_count = int(part.get("word_count", 0) or 0)
    wpm = float(part.get("wpm", 0.0) or 0.0)
    has_terminal = _has_terminal_punctuation(sentence)

    if (
        config.REFERENCE_SHORT_PART_TERMINAL_PROTECTION_ENABLED
        and has_terminal
    ):
        if (
            word_count >= config.REFERENCE_SHORT_PART_TERMINAL_KEEP_MIN_WORDS
            or duration_sec
            >= config.REFERENCE_SHORT_PART_TERMINAL_KEEP_MIN_DURATION_SEC
        ):
            return False
    if word_count >= config.REFERENCE_SHORT_PART_KEEP_MIN_WORDS:
        return False

    is_fragment = word_count <= config.REFERENCE_SHORT_PART_FRAGMENT_MAX_WORDS
    low_wpm_fragment = wpm <= config.REFERENCE_SHORT_PART_MERGE_MAX_WPM
    return is_fragment or low_wpm_fragment


def merge_short_reference_parts(
    parts: list[dict],
    min_duration_sec: float = config.REFERENCE_MIN_PART_DURATION_SEC,
    max_gap_sec: float = config.REFERENCE_MAX_PART_MERGE_GAP_SEC,
) -> list[dict]:
    """지나치게 짧은 레퍼런스 part 를 인접 part 와 병합합니다."""
    if len(parts) < 2:
        return parts

    merged_parts = list(parts)
    while True:
        changed = False
        idx = 0
        while idx < len(merged_parts):
            part = merged_parts[idx]
            if not _should_merge_short_reference_part(
                part,
                min_duration_sec,
            ):
                idx += 1
                continue

            prev_gap = float("inf")
            next_gap = float("inf")
            if idx > 0:
                prev_gap = max(
                    0.0,
                    part.get("start_sec", 0.0)
                    - merged_parts[idx - 1].get("end_sec", 0.0),
                )
            if idx + 1 < len(merged_parts):
                next_gap = max(
                    0.0,
                    merged_parts[idx + 1].get("start_sec", 0.0)
                    - part.get("end_sec", 0.0),
                )

            can_merge_prev = True
            can_merge_next = True
            if idx > 0:
                can_merge_prev = _parts_have_compatible_speakers(
                    merged_parts[idx - 1],
                    part,
                )
            if idx + 1 < len(merged_parts):
                can_merge_next = _parts_have_compatible_speakers(
                    part,
                    merged_parts[idx + 1],
                )

            if not can_merge_prev and not can_merge_next:
                idx += 1
                continue

            merge_with_prev = False
            if idx == 0:
                merge_with_prev = False
            elif idx == len(merged_parts) - 1:
                merge_with_prev = True
            elif not can_merge_prev:
                merge_with_prev = False
            elif not can_merge_next:
                merge_with_prev = True
            elif prev_gap <= max_gap_sec or next_gap <= max_gap_sec:
                merge_with_prev = prev_gap <= next_gap
            else:
                merge_with_prev = prev_gap <= next_gap

            left_idx = idx - 1 if merge_with_prev else idx
            left_part = merged_parts[left_idx]
            right_part = merged_parts[left_idx + 1]
            merged_words = sorted(
                left_part.get("word_timestamps", [])
                + right_part.get("word_timestamps", []),
                key=lambda word: (
                    word.get("start", 0.0),
                    word.get("end", 0.0),
                ),
            )
            merged_sentence = " ".join(
                text.strip()
                for text in [
                    left_part.get("sentence", ""),
                    right_part.get("sentence", ""),
                ]
                if text and text.strip()
            )
            merged_sentence = " ".join(merged_sentence.split())
            target_words = merged_sentence.split()
            part_source = (
                "turn"
                if left_part.get("part_source") == "turn"
                or right_part.get("part_source") == "turn"
                else "sentence"
            )
            left_turn_id = left_part.get("turn_id")
            right_turn_id = right_part.get("turn_id")
            turn_id = (
                left_turn_id
                if left_turn_id is not None and left_turn_id == right_turn_id
                else None
            )
            turn_break_reason = right_part.get("turn_break_reason")
            merged_part = _build_reference_part(
                merged_sentence,
                merged_words,
                target_words,
                part_source=part_source,
                turn_id=turn_id,
                turn_break_reason=turn_break_reason,
            )
            if merged_part is None:
                idx += 1
                continue

            merged_parts = (
                merged_parts[:left_idx]
                + [merged_part]
                + merged_parts[left_idx + 2 :]
            )
            changed = True
            idx = max(0, left_idx - 1)

        if not changed:
            break

    return merged_parts


def split_into_dialog_turns(
    word_timestamps: list[dict],
    gap_sec: float = config.REFERENCE_TURN_GAP_SEC,
    max_words: int = config.REFERENCE_DIALOG_TURN_MAX_WORDS,
) -> list[dict]:
    """긴 대화형 발화를 pause 기반 turn 단위로 분리합니다."""
    if not word_timestamps:
        return []

    word_timestamps = smooth_word_speaker_labels(word_timestamps)

    turns: list[dict] = []
    current_words: list[dict] = []
    turn_id = 1

    for idx, word_info in enumerate(word_timestamps):
        current_words.append(word_info)
        current_text = word_info.get("word", "").strip()
        next_gap = None
        next_word = None
        if idx + 1 < len(word_timestamps):
            next_word = word_timestamps[idx + 1]
            next_gap = word_timestamps[idx + 1].get(
                "start", 0.0
            ) - word_info.get("end", 0.0)

        current_speaker = _get_word_speaker_label(word_info)
        next_speaker = (
            _get_word_speaker_label(next_word) if next_word else None
        )
        speaker_change = bool(
            current_speaker
            and next_speaker
            and current_speaker != next_speaker
        )
        next_speaker_run = (
            _speaker_run_words(word_timestamps, idx + 1)
            if speaker_change
            else []
        )

        break_reason: str | None = None
        if current_text.endswith((".", "!", "?")):
            break_reason = "terminal_punctuation"
        elif (
            speaker_change
            and next_gap is not None
            and next_gap >= config.REFERENCE_SPEAKER_CHANGE_MIN_GAP_SEC
            and _words_meet_turn_evidence(current_words)
            and _words_meet_turn_evidence(next_speaker_run)
        ):
            break_reason = "speaker_change"
        elif next_gap is not None and next_gap >= gap_sec:
            break_reason = "pause_gap"
        elif (
            next_gap is not None
            and len(current_words) >= max_words
            and next_gap >= gap_sec * 0.5
        ):
            break_reason = "dialog_chunk"
        elif idx == len(word_timestamps) - 1:
            break_reason = "end_of_clip"

        if break_reason is None:
            continue

        sentence = " ".join(w.get("word", "").strip() for w in current_words)
        sentence = " ".join(sentence.split())
        target_words = sentence.split()
        part = _build_reference_part(
            sentence,
            current_words,
            target_words,
            part_source="turn",
            turn_id=turn_id,
            turn_break_reason=break_reason,
        )
        if part is not None:
            turns.append(part)
            turn_id += 1
        current_words = []

    return turns


def split_into_sentences_with_timestamps(
    full_text: str,
    word_timestamps: list[dict],
) -> list[dict]:
    """
    전체 텍스트를 문법적 문장 단위로 분리하고, word_timestamps 를 매핑하여
    각 문장의 start_sec / end_sec 및 학습 메타데이터를 계산합니다.
    """
    if not full_text.strip() or not word_timestamps:
        return []

    word_timestamps = smooth_word_speaker_labels(word_timestamps)
    raw_sentences = re.split(r"(?<=[.!?])\s+", full_text.strip())

    sentence_data: list[dict] = []
    word_idx = 0
    total_words = len(word_timestamps)

    for raw_sent in raw_sentences:
        sentence = raw_sent.strip()
        if not sentence:
            continue

        target_words = sentence.split()
        words_to_collect = len(target_words)

        sentence_words: list[dict] = []
        consumed_word_count = 0
        while (
            word_idx < total_words and consumed_word_count < words_to_collect
        ):
            entry = word_timestamps[word_idx]
            sentence_words.append(entry)
            entry_word_count = len(entry.get("word", "").split())
            consumed_word_count += max(1, entry_word_count)
            word_idx += 1

        if not sentence_words:
            continue

        part = _build_reference_part(
            sentence,
            sentence_words,
            target_words,
        )
        if part is not None:
            sentence_data.append(part)

    dialog_turns = split_into_dialog_turns(word_timestamps)
    detected_speakers = {
        label
        for label in (
            _get_word_speaker_label(word) for word in word_timestamps
        )
        if label
    }
    should_use_turns = (
        len(detected_speakers) >= 2 and len(dialog_turns) >= 2
    ) or (
        len(dialog_turns) > len(sentence_data)
        and (
            len(sentence_data) <= 1
            or any(
                part.get("word_count", 0)
                > config.REFERENCE_DIALOG_TURN_MAX_WORDS * 2
                for part in sentence_data
            )
        )
    )
    if should_use_turns:
        return merge_short_reference_parts(dialog_turns)

    return merge_short_reference_parts(sentence_data)
