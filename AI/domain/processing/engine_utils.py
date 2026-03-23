"""StyleEcho 공유 유틸리티.

engine.py 에서 분리된 공통 함수/상수.
여러 하위 모듈(text_processing, speaker_analysis, quality, pipeline 등)이
순환 import 없이 참조할 수 있는 최하위 레이어입니다.
"""

import re

import config
import jiwer
from domain.processing.constants import REDUCTION_PATTERNS

_REMOVE_PUNCT = jiwer.RemovePunctuation()
_CLEAN_WORD_RE = re.compile(r"[^a-zA-Z']")
_MULTI_SPACE_RE = re.compile(r"\s+")


def _normalize_alignment_text(text: str) -> str:
    """평가 정렬용으로 구두점을 정리하고 소문자 기준으로 정규화합니다.

    Args:
        text: 원본 텍스트.

    Returns:
        정규화된 텍스트.
    """
    cleaned = _CLEAN_WORD_RE.sub(" ", text.lower())
    return _MULTI_SPACE_RE.sub(" ", cleaned).strip()


def _build_canonical_phrase_map() -> dict[str, tuple[str, ...]]:
    """연음/축약 표현을 공통 canonical token 시퀀스로 매핑합니다.

    Returns:
        정규화된 표현 → canonical 토큰 튜플 매핑.
    """
    phrase_map: dict[str, tuple[str, ...]] = {}
    for original, reduced in REDUCTION_PATTERNS.items():
        canonical = tuple(_normalize_alignment_text(original).split())
        if not canonical:
            continue
        phrase_map[_normalize_alignment_text(original)] = canonical
        phrase_map[_normalize_alignment_text(reduced)] = canonical
    return phrase_map


_CANONICAL_PHRASE_MAP = _build_canonical_phrase_map()


def _normalize_word(text: str) -> str:
    """구두점을 제거하고 소문자 기준으로 단어를 정규화합니다.

    Args:
        text: 원본 단어 문자열.

    Returns:
        정규화된 단어.
    """
    return " ".join(_canonicalize_tokens(text))


def _canonicalize_tokens(text: str) -> list[str]:
    """연음/축약을 원형 기준 토큰 시퀀스로 변환합니다.

    Args:
        text: 원본 텍스트.

    Returns:
        canonical 토큰 리스트.
    """
    normalized = _normalize_alignment_text(text)
    if not normalized:
        return []
    return list(
        _CANONICAL_PHRASE_MAP.get(normalized, tuple(normalized.split()))
    )


def _sum_word_durations(words: list[dict]) -> float:
    """단어 타임스탬프 길이 합으로 발화 시간을 계산합니다.

    Args:
        words: 단어 타임스탬프 리스트.

    Returns:
        총 발화 시간(초).
    """
    return sum(
        max(0.0, word.get("end", 0.0) - word.get("start", 0.0))
        for word in words
    )


def count_pauses_from_words(
    words: list[dict],
    threshold: float = config.PAUSE_GAP_SEC,
) -> int:
    """단어 간 gap 이 threshold 보다 큰 구간 수를 pause 로 계산합니다.

    Args:
        words: 단어 타임스탬프 리스트.
        threshold: 휴지기 판정 최소 gap(초).

    Returns:
        휴지기 횟수.
    """
    if len(words) < 2:
        return 0

    return sum(
        1
        for idx in range(1, len(words))
        if words[idx].get("start", 0.0) - words[idx - 1].get("end", 0.0)
        > threshold
    )


def extract_pause_positions(
    words: list[dict],
    threshold: float = config.PAUSE_ALIGN_GAP_SEC,
) -> set[int]:
    """단어 간 gap > threshold 인 위치(단어 인덱스)의 집합을 반환합니다.

    인덱스 ``i`` 가 결과에 포함되었다는 것은 ``words[i]`` 뒤에
    threshold 초 이상의 휴지기가 존재함을 의미합니다.

    Args:
        words: WhisperX 단어 타임스탬프 리스트.
        threshold: 휴지기로 판정할 최소 gap(초).

    Returns:
        휴지기가 발생한 단어 인덱스 집합.
    """
    if len(words) < 2:
        return set()

    return {
        idx
        for idx in range(len(words) - 1)
        if words[idx + 1].get("start", 0.0) - words[idx].get("end", 0.0)
        > threshold
    }


def _count_word_tokens(word: dict) -> int:
    """타임스탬프 항목이 표현하는 단어 수를 계산합니다.

    Args:
        word: 단어 타임스탬프 딕셔너리.

    Returns:
        단어 토큰 수 (최소 1).
    """
    return max(1, len(word.get("word", "").split()))
