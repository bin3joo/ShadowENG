"""레퍼런스 페이로드 헬퍼 함수."""

import re
from typing import Any

import numpy as np
from domain.processing.engine_utils import (
    _sum_word_durations,
    count_pauses_from_words,
)

_DISALLOWED_SCRIPT_CHAR_RE = re.compile(r"[^A-Za-z0-9\s'.,!?]")
_MULTI_SPACE_RE = re.compile(r"\s+")
_SPACE_BEFORE_PUNCT_RE = re.compile(r"\s+([.,!?])")


def _numpy_to_python(obj: Any) -> Any:
    """NumPy 값을 JSON 직렬화 가능한 Python 값으로 변환합니다.

    Args:
        obj: NumPy 스칼라 또는 배열 값을 포함할 수 있는 임의 객체.

    Returns:
        변환이 필요한 경우 네이티브 Python 값, 그렇지 않으면 원본 객체.
    """
    if isinstance(obj, np.integer):
        return int(obj)
    if isinstance(obj, np.floating):
        return float(obj)
    if isinstance(obj, np.ndarray):
        return obj.tolist()
    return obj


def sanitize_reference_text(text: str) -> str:
    """기본 문장 구두점을 유지하면서 트랜스크립트 텍스트를 정제합니다.

    Args:
        text: 원본 트랜스크립트 텍스트.

    Returns:
        정제된 트랜스크립트 텍스트.
    """
    normalized = text.replace("\r", " ").replace("\n", " ")
    normalized = _DISALLOWED_SCRIPT_CHAR_RE.sub(" ", normalized)
    normalized = _MULTI_SPACE_RE.sub(" ", normalized).strip()
    normalized = _SPACE_BEFORE_PUNCT_RE.sub(r"\1", normalized)
    return normalized


def _build_public_word_timestamps(
    words: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """공개 응답용 정제된 단어 타임스탬프를 생성합니다.

    Args:
        words: 내부 단어 타임스탬프 페이로드 리스트.

    Returns:
        공개용 단어 타임스탬프 리스트.
    """
    public_words: list[dict[str, Any]] = []
    for word in words:
        clean_word = sanitize_reference_text(word.get("word", ""))
        if not clean_word:
            continue
        public_words.append(
            {
                "word": clean_word,
                "start": float(word.get("start", 0.0)),
                "end": float(word.get("end", 0.0)),
            }
        )
    return public_words


def _build_public_parts(
    sentence_data: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """공개용 파트 페이로드를 생성합니다.

    Args:
        sentence_data: 내부 레퍼런스 파트 페이로드 리스트.

    Returns:
        공개 응답용 파트 리스트.
    """
    public_parts: list[dict[str, Any]] = []
    for part in sentence_data:
        public_parts.append(
            {
                "sentence": part.get("sentence", ""),
                "start_sec": float(part.get("start_sec", 0.0)),
                "end_sec": float(part.get("end_sec", 0.0)),
                "duration_sec": float(part.get("duration_sec", 0.0)),
                "difficulty_score": float(part.get("difficulty_score", 0.0)),
                "difficulty": part.get("difficulty", "Normal"),
                "key_expressions": list(part.get("key_expressions", [])),
                "word_timestamps": _build_public_word_timestamps(
                    part.get("word_timestamps", [])
                ),
                "pause_count": int(part.get("pause_count", 0)),
                "features": part.get("features"),
                "sentence_ko": part.get("sentence_ko"),
                "source_part_ids": list(part.get("source_part_ids", [])),
                "vocabulary": list(part.get("vocabulary", [])),
            }
        )
    return public_parts


def sanitize_word_timestamps(
    words: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """단어 타임스탬프 텍스트를 정제하고 구두점만 있는 항목을 제거합니다.

    Args:
        words: 내부 단어 타임스탬프 페이로드 리스트.

    Returns:
        정제된 단어 타임스탬프 리스트.
    """
    sanitized_words: list[dict[str, Any]] = []
    for word in words:
        clean_word = sanitize_reference_text(word.get("word", ""))
        if not clean_word:
            continue

        sanitized_word = dict(word)
        sanitized_word["word"] = clean_word
        sanitized_words.append(sanitized_word)

    return sanitized_words


def attach_part_analysis(
    sentence_data: list[dict[str, Any]],
    f0: np.ndarray,
    rms: np.ndarray,
    speech_start_sec: float,
    target_sr: int,
    hop_length: int,
) -> list[dict[str, Any]]:
    """문장 파트에 억양 특징과 pause 수를 추가합니다.

    Args:
        sentence_data: 레퍼런스 파트 페이로드 리스트.
        f0: F0 특징 배열.
        rms: RMS 특징 배열.
        speech_start_sec: 요청 클립 내 발화 시작 시간(초).
        target_sr: 특징 추출 샘플레이트.
        hop_length: 특징 홈 길이.

    Returns:
        갱신된 레퍼런스 파트 페이로드 리스트.
    """
    for part in sentence_data:
        part_words = part.get("word_timestamps", [])
        word_starts = [
            word.get("start") for word in part_words if "start" in word
        ]
        word_ends = [word.get("end") for word in part_words if "end" in word]

        part_start_sec = min(word_starts) if word_starts else part["start_sec"]
        part_end_sec = max(word_ends) if word_ends else part["end_sec"]

        rel_start = max(0.0, part_start_sec - speech_start_sec)
        rel_end = max(rel_start, part_end_sec - speech_start_sec)

        frame_start = int(rel_start * target_sr / hop_length)
        frame_end = int(rel_end * target_sr / hop_length)

        part["features"] = {
            "f0_array": _numpy_to_python(f0[frame_start:frame_end]),
            "rms_array": _numpy_to_python(rms[frame_start:frame_end]),
        }
        part["pause_count"] = count_pauses_from_words(part_words)

    return sentence_data


def build_reference_response(
    video_id: str,
    start_sec: float,
    end_sec: float,
    final_script: str,
    sentence_data: list[dict[str, Any]],
    trimmed_word_count: int,
    final_words: list[dict[str, Any]],
    quality_metadata: dict[str, Any] | None = None,
    translation_metadata: dict[str, Any] | None = None,
    hop_length: int | None = None,
) -> dict[str, Any]:
    """``generate-reference`` 응답 페이로드를 생성합니다.

    Args:
        video_id: YouTube 비디오 식별자.
        start_sec: 요청 시작 시간(초).
        end_sec: 요청 종료 시간(초).
        final_script: 최종 정제된 트랜스크립트.
        sentence_data: 레퍼런스 파트 페이로드 리스트.
        trimmed_word_count: 경계 정제된 단어 수.
        final_words: 최종 단어 타임스탬프 리스트.
        quality_metadata: 레퍼런스 품질 메타데이터 (선택).
        translation_metadata: 번역 메타데이터 (선택).
        hop_length: 레퍼런스에 사용된 특징 홈 길이 (선택).

    Returns:
        직렬화된 API 응답 페이로드.
    """
    translation_metadata = translation_metadata or {}
    payload = {
        "status": "SUCCESS",
        "video_id": video_id,
        "start_sec": start_sec,
        "end_sec": end_sec,
        "final_script": final_script,
        "final_script_ko": translation_metadata.get("final_script_ko"),
        "parts": _build_public_parts(sentence_data),
        "trimmed_word_count": trimmed_word_count,
        "pause_count": count_pauses_from_words(final_words),
        "active_speech_sec": round(_sum_word_durations(final_words), 3),
        "word_count": len(final_script.split()),
        "reference_quality": "good",
        "quality_reasons": [],
        "warnings": [],
        "learning_expressions": [],
        "translation_success": False,
        "translation_retry_count": 0,
        "translation_provider": None,
        "hop_length": hop_length,
    }
    if quality_metadata:
        payload.update(
            {
                "reference_quality": quality_metadata.get(
                    "reference_quality",
                    "good",
                ),
                "quality_reasons": list(
                    quality_metadata.get("quality_reasons", [])
                ),
                "warnings": list(quality_metadata.get("warnings", [])),
            }
        )
    if translation_metadata:
        payload.update(
            {
                "final_script_ko": translation_metadata.get("final_script_ko"),
                "learning_expressions": list(
                    translation_metadata.get("learning_expressions", [])
                ),
                "translation_success": (
                    translation_metadata.get("translation_status") == "success"
                ),
                "translation_retry_count": int(
                    translation_metadata.get("translation_retry_count", 0)
                ),
                "translation_provider": translation_metadata.get(
                    "translation_provider"
                ),
            }
        )
    return payload
