"""
StyleEcho 공유 유틸리티
======================
engine.py 에서 분리된 공통 함수/상수.
여러 하위 모듈(text_processing, speaker_analysis, quality, pipeline 등)이
순환 import 없이 참조할 수 있는 최하위 레이어입니다.
"""

import re

import jiwer

try:
    from ... import config
except ImportError:
    import config

_REMOVE_PUNCT = jiwer.RemovePunctuation()
_CLEAN_WORD_RE = re.compile(r"[^a-zA-Z']")


def _normalize_word(text: str) -> str:
    """구두점을 제거하고 소문자 기준으로 단어를 정규화합니다."""
    return " ".join(_REMOVE_PUNCT(text.lower()).split())


def _sum_word_durations(words: list[dict]) -> float:
    """단어 타임스탬프 길이 합으로 발화 시간을 계산합니다."""
    return sum(
        max(0.0, word.get("end", 0.0) - word.get("start", 0.0))
        for word in words
    )


def count_pauses_from_words(
    words: list[dict],
    threshold: float = config.PAUSE_GAP_SEC,
) -> int:
    """단어 간 gap 이 threshold 보다 큰 구간 수를 pause 로 계산합니다."""
    if len(words) < 2:
        return 0

    return sum(
        1
        for idx in range(1, len(words))
        if words[idx].get("start", 0.0) - words[idx - 1].get("end", 0.0)
        > threshold
    )


def _count_word_tokens(word: dict) -> int:
    """타임스탬프 항목이 표현하는 단어 수를 계산합니다."""
    return max(1, len(word.get("word", "").split()))
