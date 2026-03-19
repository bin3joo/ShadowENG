"""
StyleEcho 화자 분석
===================
단어 레벨 화자 라벨 처리, 파트별 화자 메타데이터 산출,
speaker label smoothing 등 화자 관련 로직을 담당합니다.
"""

import logging
from collections import Counter

import config
from domain.processing.engine_utils import _count_word_tokens

logger = logging.getLogger(__name__)


def _get_word_speaker_label(word: dict) -> str | None:
    """단어 타임스탬프에서 화자 라벨을 읽어옵니다."""
    if not word:
        return None
    speaker = word.get("speaker")
    if speaker is None:
        return None
    return str(speaker)


def _speaker_run_words(
    word_timestamps: list[dict], start_idx: int
) -> list[dict]:
    """특정 인덱스부터 이어지는 동일 화자 구간을 반환합니다."""
    if start_idx < 0 or start_idx >= len(word_timestamps):
        return []

    label = _get_word_speaker_label(word_timestamps[start_idx])
    if not label:
        return []

    run_words: list[dict] = []
    for idx in range(start_idx, len(word_timestamps)):
        word = word_timestamps[idx]
        if _get_word_speaker_label(word) != label:
            break
        run_words.append(word)
    return run_words


def _dominant_speaker_label(words: list[dict]) -> str | None:
    """단어 리스트에서 대표 화자 라벨을 계산합니다."""
    labels = [_get_word_speaker_label(word) for word in words]
    labels = [label for label in labels if label]
    if not labels:
        return None
    return Counter(labels).most_common(1)[0][0]


def _parts_have_compatible_speakers(left_part: dict, right_part: dict) -> bool:
    """짧은 part 병합 시 화자 경계를 보존할 수 있는지 판단합니다."""
    left_label = _dominant_speaker_label(left_part.get("word_timestamps", []))
    right_label = _dominant_speaker_label(
        right_part.get("word_timestamps", [])
    )
    if not left_label or not right_label:
        return True
    return left_label == right_label


def smooth_word_speaker_labels(
    word_timestamps: list[dict],
    max_words: int = config.REFERENCE_SPEAKER_LABEL_SMOOTHING_MAX_WORDS,
    max_duration_sec: float = (
        config.REFERENCE_SPEAKER_LABEL_SMOOTHING_MAX_DURATION_SEC
    ),
) -> list[dict]:
    """짧은 화자 라벨 흔들림을 smoothing 합니다.

    예: [A, A, B, A, A] 에서 B 가 1~2 단어이고 duration 이 짧으면
    주변 화자 A 로 교체합니다.
    """
    if len(word_timestamps) < 3:
        return word_timestamps

    smoothed_words = [dict(w) for w in word_timestamps]

    for _iteration in range(3):
        changed = False
        idx = 0
        while idx < len(smoothed_words):
            current_label = _get_word_speaker_label(smoothed_words[idx])
            if current_label is None:
                idx += 1
                continue

            run_start = idx
            run_end = idx + 1
            while run_end < len(smoothed_words):
                next_label = _get_word_speaker_label(smoothed_words[run_end])
                if next_label != current_label:
                    break
                run_end += 1

            run_words = smoothed_words[run_start:run_end]
            run_word_count = sum(_count_word_tokens(w) for w in run_words)

            starts = [w.get("start", 0.0) for w in run_words if "start" in w]
            ends = [w.get("end", 0.0) for w in run_words if "end" in w]
            run_duration = (
                (max(ends) - min(starts)) if starts and ends else 0.0
            )

            if (
                run_word_count <= max_words
                and run_duration <= max_duration_sec
            ):
                prev_label = (
                    _get_word_speaker_label(smoothed_words[run_start - 1])
                    if run_start > 0
                    else None
                )
                next_label_after = (
                    _get_word_speaker_label(smoothed_words[run_end])
                    if run_end < len(smoothed_words)
                    else None
                )

                replace_label = None
                if prev_label and prev_label == next_label_after:
                    replace_label = prev_label
                elif prev_label and not next_label_after:
                    replace_label = prev_label
                elif next_label_after and not prev_label:
                    replace_label = next_label_after

                if replace_label and replace_label != current_label:
                    for i in range(run_start, run_end):
                        smoothed_words[i]["speaker"] = replace_label
                    changed = True

            idx = run_end

        if not changed:
            return smoothed_words

    return smoothed_words


def _speaker_token_counts(words: list[dict]) -> Counter[str]:
    """단어 리스트의 화자별 token 수를 집계합니다."""
    counts: Counter[str] = Counter()
    for word in words:
        label = _get_word_speaker_label(word)
        if not label:
            continue
        counts[label] += _count_word_tokens(word)
    return counts


def annotate_reference_part_speakers(sentence_data: list[dict]) -> dict:
    """part 별 대표 화자 및 화자 수 메타데이터를 채웁니다.

    Returns
    -------
    dict
        diarization_used, detected_speaker_count 등 집계 통계.
    """
    detected_speakers: set[str] = set()
    dominant_speakers: list[str] = []
    all_words: list[dict] = []

    for part in sentence_data:
        part_words = part.get("word_timestamps", [])
        all_words.extend(part_words)
        dom = _dominant_speaker_label(part_words)
        part["dominant_speaker"] = dom
        if dom:
            dominant_speakers.append(dom)

        speaker_counts = _speaker_token_counts(part_words)
        unique_speakers = set(speaker_counts.keys())
        detected_speakers.update(unique_speakers)
        part["speaker_count"] = len(unique_speakers)

        total_tokens = sum(speaker_counts.values())
        if total_tokens > 0 and dom:
            dom_ratio = speaker_counts[dom] / total_tokens
            if (
                dom_ratio
                < config.REFERENCE_SINGLE_SPEAKER_PART_DOMINANCE_RATIO
            ):
                part["speaker_risk"] = "high"

    diarization_used = any(
        _get_word_speaker_label(w) is not None for w in all_words
    )
    detected_speaker_count = len(detected_speakers)

    global_counts = _speaker_token_counts(all_words)
    total_global_tokens = sum(global_counts.values())

    if total_global_tokens > 0 and global_counts:
        sorted_speakers = global_counts.most_common()
        dominant_speaker_word_ratio = (
            sorted_speakers[0][1] / total_global_tokens
        )
        second_speaker_word_ratio = (
            sorted_speakers[1][1] / total_global_tokens
            if len(sorted_speakers) > 1
            else 0.0
        )
    else:
        dominant_speaker_word_ratio = 1.0
        second_speaker_word_ratio = 0.0

    label_changes = 0
    prev_label = None
    for word in all_words:
        label = _get_word_speaker_label(word)
        if label and label != prev_label:
            if prev_label is not None:
                label_changes += 1
            prev_label = label

    speaker_label_change_ratio = label_changes / max(1, len(all_words))

    multi_speaker_part_count = sum(
        1 for part in sentence_data if part.get("speaker_count", 0) >= 2
    )
    multi_speaker_part_ratio = multi_speaker_part_count / max(
        1, len(sentence_data)
    )

    dominant_speaker_parts = 0
    if dominant_speakers:
        global_dominant = Counter(dominant_speakers).most_common(1)[0][0]
        dominant_speaker_parts = sum(
            1
            for part in sentence_data
            if part.get("dominant_speaker") == global_dominant
        )
    dominant_speaker_part_ratio = dominant_speaker_parts / max(
        1, len(sentence_data)
    )

    return {
        "diarization_used": diarization_used,
        "detected_speaker_count": detected_speaker_count,
        "dominant_speaker_word_ratio": round(dominant_speaker_word_ratio, 3),
        "second_speaker_word_ratio": round(second_speaker_word_ratio, 3),
        "speaker_label_change_ratio": round(
            speaker_label_change_ratio,
            3,
        ),
        "multi_speaker_part_ratio": round(multi_speaker_part_ratio, 3),
        "dominant_speaker_part_ratio": round(
            dominant_speaker_part_ratio,
            3,
        ),
    }
