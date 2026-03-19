"""Gemini-based translation service for reference payloads."""

import json
import logging
import os
import threading
import time
from typing import Any

import config
from domain.processing.speaker_analysis import (
    _dominant_speaker_label,
    _get_word_speaker_label,
)
from domain.processing.text_processing import _build_reference_part
from google import genai
from google.genai import types
from pydantic import BaseModel, Field, ValidationError

logger = logging.getLogger(__name__)
_gemini_client: Any | None = None
_gemini_client_lock = threading.Lock()


class LearningExpression(BaseModel):
    """A useful learning expression extracted from the reference."""

    expression: str
    meaning: str
    pronunciation_en: str
    pronunciation_ko: str
    nuance_in_sentence: str
    example_en: str
    example_ko: str


class PartVocabulary(BaseModel):
    """A vocabulary item extracted for a merged part."""

    word: str = Field(min_length=1)
    meaning_ko: str = Field(min_length=1)
    phonetic_en: str = Field(min_length=1)
    phonetic_ko: str = Field(min_length=1)
    example_en: str = Field(min_length=1)
    example_ko: str = Field(min_length=1)


class GeminiMergedPart(BaseModel):
    """A merged part returned by Gemini."""

    source_part_ids: list[str] = Field(min_length=1)
    translated_text: str = Field(min_length=1)
    vocabulary: list[PartVocabulary] = Field(default_factory=list)


class GeminiTranslationResponse(BaseModel):
    """Structured Gemini response for reference translation."""

    full_text_translation: str = Field(min_length=1)
    merged_parts: list[GeminiMergedPart] = Field(min_length=1)
    learning_expressions: list[LearningExpression] = Field(
        default_factory=list
    )


class TranslationMetadata(BaseModel):
    """Translation metadata attached to the response payload."""

    final_script_ko: str | None
    parts: list[dict[str, Any]]
    learning_expressions: list[dict[str, Any]]
    translation_status: str
    translation_retry_count: int
    translation_provider: str


class GeminiRetryableError(RuntimeError):
    """Retryable Gemini request error."""


def _parse_vocabulary_items(raw_items: Any) -> list[PartVocabulary]:
    """Parse valid vocabulary items and drop malformed entries."""
    parsed_items: list[PartVocabulary] = []
    if not isinstance(raw_items, list):
        return parsed_items

    for index, item in enumerate(raw_items, start=1):
        try:
            parsed_items.append(PartVocabulary.model_validate(item))
        except ValidationError as exc:
            logger.warning(
                "Dropping invalid vocabulary item #%d: %s",
                index,
                exc,
            )
    return parsed_items


def _parse_merged_parts(raw_parts: Any) -> list[GeminiMergedPart]:
    """Parse merged parts while tolerating malformed vocabulary items."""
    if not isinstance(raw_parts, list) or not raw_parts:
        raise ValueError("Gemini merged_parts must be a non-empty list.")

    parsed_parts: list[GeminiMergedPart] = []
    for index, part in enumerate(raw_parts, start=1):
        if not isinstance(part, dict):
            raise ValueError(
                f"Gemini merged_parts[{index}] must be an object."
            )

        normalized_part = {
            "source_part_ids": part.get("source_part_ids", []),
            "translated_text": part.get("translated_text", ""),
            "vocabulary": [
                item.model_dump()
                for item in _parse_vocabulary_items(part.get("vocabulary", []))
            ],
        }

        try:
            parsed_parts.append(
                GeminiMergedPart.model_validate(normalized_part)
            )
        except ValidationError as exc:
            raise ValueError(
                f"Gemini merged_parts[{index}] validation failed: {exc}"
            ) from exc

    return parsed_parts


def _parse_learning_expressions(raw_items: Any) -> list[LearningExpression]:
    """Parse valid learning expressions and drop malformed entries."""
    parsed_items: list[LearningExpression] = []
    if not isinstance(raw_items, list):
        return parsed_items

    for index, item in enumerate(raw_items, start=1):
        try:
            parsed_items.append(LearningExpression.model_validate(item))
        except ValidationError as exc:
            logger.warning(
                "Dropping invalid learning expression #%d: %s",
                index,
                exc,
            )
    return parsed_items


def _parse_translation_response(
    raw_response: dict[str, Any],
) -> tuple[str, list[GeminiMergedPart], list[LearningExpression]]:
    """Parse a Gemini translation response while salvaging valid optional items."""
    full_text_translation = str(
        raw_response.get("full_text_translation", "")
    ).strip()
    if not full_text_translation:
        raise ValueError("Gemini full_text_translation must be non-empty.")

    merged_parts = _parse_merged_parts(raw_response.get("merged_parts", []))
    learning_expressions = _parse_learning_expressions(
        raw_response.get("learning_expressions", [])
    )
    return full_text_translation, merged_parts, learning_expressions


def _strip_code_fence(text: str) -> str:
    """Remove optional Markdown code fences from model output."""
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()
    return cleaned


def _get_gemini_api_key() -> str:
    """Return the configured Gemini API key from environment variables."""
    return (
        os.getenv("GMS_API_KEY", "").strip()
        or os.getenv("GEMINI_API_KEY", "").strip()
    )


def _get_gemini_client() -> Any:
    """Create and cache the Gemini SDK client."""
    global _gemini_client
    if _gemini_client is not None:
        return _gemini_client

    with _gemini_client_lock:
        if _gemini_client is None:
            api_key = _get_gemini_api_key()
            if not api_key:
                raise RuntimeError("GMS_API_KEY is not configured.")
            _gemini_client = genai.Client(
                api_key=api_key,
                http_options={"base_url": config.LLM_GEMINI_BASE_URL},
            )
    return _gemini_client


def _get_part_speaker_hint(part: dict[str, Any]) -> str | None:
    """Return the dominant speaker label for a part if available.

    Args:
        part: Reference part payload.

    Returns:
        Dominant speaker label if available, otherwise ``None``.
    """
    return _dominant_speaker_label(part.get("word_timestamps", []))


def _build_llm_input_parts(
    sentence_data: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Build a compact preprocessed part list for Gemini.

    Args:
        sentence_data: Reference part payload list.

    Returns:
        Preprocessed part list used to build the Gemini prompt.
    """
    payload_parts: list[dict[str, Any]] = []
    for index, part in enumerate(sentence_data, start=1):
        payload_parts.append(
            {
                "part_index": index,
                "part_id": f"part{index}",
                "start_sec": float(part.get("start_sec", 0.0) or 0.0),
                "end_sec": float(part.get("end_sec", 0.0) or 0.0),
                "sentence": part.get("sentence", ""),
                "word_count": int(part.get("word_count", 0) or 0),
                "duration_sec": float(part.get("duration_sec", 0.0) or 0.0),
                "part_source": part.get("part_source", "sentence"),
                "turn_id": part.get("turn_id"),
                "speaker_hint": _get_part_speaker_hint(part),
                "ends_with_terminal": str(part.get("sentence", ""))
                .rstrip()
                .endswith((".", "?", "!")),
            }
        )
    return payload_parts


def _build_prompt(
    full_text: str,
    sentence_data: list[dict[str, Any]],
) -> str:
    """Build the Gemini prompt for translation and contextual merging.

    Args:
        full_text: Full reference transcript.
        sentence_data: Reference part payload list.

    Returns:
        Prompt string for Gemini.
    """
    preprocessed_parts = _build_llm_input_parts(sentence_data)
    payload = {
        "full_text": full_text,
        "parts": preprocessed_parts,
    }
    return (
        "You are processing English learning reference text. "
        "Return JSON only. Do not wrap the response in markdown.\n"
        "Tasks:\n"
        "1. [CRITICAL] Translate the full English text into extremely natural, fluent Korean (구어체). Act as a professional movie subtitle translator. Actively omit unnatural pronouns (e.g., 나는, 당신은, 그것은) and completely avoid word-for-word literal translation.\n"
        "2. Merge adjacent preprocessed parts ONLY IF they form a logically and grammatically continuous thought AND the resulting combined duration does not exceed 15 seconds. Ignore external speaker_hints if they conflict with logical continuity.\n"
        "3. Keep questions or speaker changes separate unless there is very strong evidence to merge.\n"
        "4. For each final merged part, provide source_part_ids, translated_text, and vocabulary. Ensure 'translated_text' matches the movie-subtitle quality.\n"
        "5. Extract 3 to 5 highly reusable English learning expressions based on the rules below.\n"
        "6. For each merged part, extract 3 to 5 highly useful expressions, idioms, slang items, collocations, or key vocabulary.\n"
        "7. For each vocabulary item, provide word, meaning_ko, phonetic_en, phonetic_ko, example_en, and example_ko.\n"
        "8. For each learning_expressions item, provide expression, meaning, pronunciation_en, pronunciation_ko, nuance_in_sentence, example_en, and example_ko.\n"
        "Rules:\n"
        "- [CRITICAL TRANSLATION RULE] Prioritize natural Korean phrasing and localization over literal translation. The output MUST sound like a natural Korean conversation. Maintain a consistent and context-appropriate politeness level (존댓말 or 반말) throughout the dialogue.\n"
        "- [CRITICAL MERGE RULE] The total combined duration of a merged part (calculated as the final 'end_sec' minus the initial 'start_sec', or the sum of 'duration_sec') MUST NOT exceed 15 seconds. If adding the next adjacent part would exceed 15 seconds, you MUST stop merging and start a new merged part.\n"
        "- source_part_ids must cover every part exactly once and keep the original order.\n"
        "- Merge only adjacent parts. Do not omit any source part.\n"
        "- Preserve part metadata from the input when deciding merges, especially part_index, start_sec, end_sec, part_id, and sentence.\n"
        "- Each merged part vocabulary list must be created from that merged part only, not from the full text in general.\n"
        "- Avoid overly basic words such as articles, be-verbs, pronouns, and names unless they are part of a real idiom or useful expression.\n"
        "- phonetic_en must be written as English IPA.\n"
        "- phonetic_ko must describe how the expression is naturally read in Hangeul based on actual pronunciation, not simple letter-by-letter transliteration.\n"
        "- example_en must be short, practical, and reusable for learners.\n"
        '- For learning_expressions, avoid interjections (e.g., "Ugh", "Ah"), names, or overly specific full sentences that are not reusable.\n'
        "- Prefer chunk-level expressions or sentence frames that can be applied to other situations.\n"
        "- A single word is allowed only if it is highly reusable, educationally valuable, and has a meaningful nuance in context.\n"
        "- Keep meaning concise, nuance_in_sentence within 1 to 2 sentences, and example sentences short and practical.\n"
        "- Output schema keys must be exactly: full_text_translation, merged_parts, learning_expressions.\n"
        "- Each merged_parts item must contain exactly: source_part_ids, translated_text, vocabulary.\n"
        "- Each vocabulary item must contain exactly: word, meaning_ko, phonetic_en, phonetic_ko, example_en, example_ko.\n"
        "- Each learning_expressions item must contain exactly: expression, meaning, pronunciation_en, pronunciation_ko, nuance_in_sentence, example_en, example_ko.\n"
        "- Every required field must be present and non-empty. If you cannot fill every required field for a vocabulary or learning_expressions item, omit that item entirely instead of returning a partial object.\n"
        f"Input JSON:\n{json.dumps(payload, ensure_ascii=False)}"
    )


def _build_generate_config() -> types.GenerateContentConfig:
    """Build Gemini generation config for strict JSON output.

    Returns:
        Gemini SDK generation config.
    """
    return types.GenerateContentConfig(
        system_instruction=config.LLM_GEMINI_SYSTEM_INSTRUCTION,
        temperature=config.LLM_GEMINI_TEMPERATURE,
        max_output_tokens=config.LLM_GEMINI_MAX_OUTPUT_TOKENS,
        response_mime_type="application/json",
        safety_settings=[
            types.SafetySetting(
                category=types.HarmCategory.HARM_CATEGORY_HATE_SPEECH,
                threshold=types.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE,
            ),
        ],
    )


def _get_response_finish_reason(response: Any) -> str | None:
    """Extract the first-candidate finish reason from a Gemini response."""
    candidates = getattr(response, "candidates", None)
    if not candidates:
        return None

    first_candidate = candidates[0]
    finish_reason = getattr(first_candidate, "finish_reason", None)
    if finish_reason is None:
        finish_reason = getattr(first_candidate, "finishReason", None)
    if finish_reason is None:
        return None

    enum_name = getattr(finish_reason, "name", None)
    if enum_name:
        return str(enum_name)
    return str(finish_reason)


def _request_gemini(prompt: str) -> dict[str, Any]:
    """Send a single prompt to Gemini and return the parsed JSON.

    Args:
        prompt: Prompt string for Gemini.

    Returns:
        Parsed JSON response payload.

    Raises:
        RuntimeError: If Gemini returns an empty text response.
        GeminiRetryableError: If the response is truncated due to token limits.
        json.JSONDecodeError: If the response is not valid JSON.
    """
    client = _get_gemini_client()
    response = client.models.generate_content(
        model=config.LLM_GEMINI_MODEL,
        config=_build_generate_config(),
        contents=[
            types.Content(
                role="user",
                parts=[types.Part.from_text(text=prompt)],
            )
        ],
    )

    finish_reason = _get_response_finish_reason(response)
    if finish_reason == "MAX_TOKENS":
        raise GeminiRetryableError(
            "Gemini response was truncated due to MAX_TOKENS."
        )

    text = str(getattr(response, "text", "")).strip()
    if not text:
        raise RuntimeError("Gemini returned an empty text response.")

    text = _strip_code_fence(text)
    return json.loads(text)


def _is_retryable_exception(exc: Exception) -> bool:
    """Return whether a Gemini error should be retried."""
    if isinstance(exc, GeminiRetryableError):
        return True

    message = str(exc).lower()
    retry_signals = [
        "429",
        "quota",
        "rate limit",
        "timeout",
        "timed out",
        "deadline exceeded",
        "temporarily unavailable",
        "service unavailable",
        "internal",
        "unavailable",
        "503",
        "502",
        "500",
        "max_tokens",
        "truncated",
    ]
    return any(signal in message for signal in retry_signals)


def _validate_merged_part_ids(
    sentence_data: list[dict[str, Any]],
    merged_parts: list[GeminiMergedPart],
) -> None:
    """Validate that Gemini merged parts cover all source parts in order.

    Args:
        sentence_data: Original reference part payload list.
        merged_parts: Gemini merged-part response.

    Raises:
        ValueError: If merged parts do not cover the source parts exactly once
            in order.
    """
    expected_ids = [
        f"part{index}" for index in range(1, len(sentence_data) + 1)
    ]
    actual_ids: list[str] = []
    for part in merged_parts:
        actual_ids.extend(part.source_part_ids)
    if actual_ids != expected_ids:
        raise ValueError(
            "Gemini merged_parts must cover every source part exactly once "
            "in the original order."
        )


def _merge_source_parts(
    sentence_data: list[dict[str, Any]],
    merged_parts: list[GeminiMergedPart],
) -> list[dict[str, Any]]:
    """Merge source parts according to the Gemini result.

    Args:
        sentence_data: Original reference part payload list.
        merged_parts: Gemini merged-part response.

    Returns:
        Rebuilt merged reference parts.

    Raises:
        ValueError: If a merged part cannot be rebuilt.
    """
    merged_payload_parts: list[dict[str, Any]] = []
    for merged_part in merged_parts:
        source_indexes = [
            int(part_id.replace("part", "")) - 1
            for part_id in merged_part.source_part_ids
        ]
        source_parts = [sentence_data[index] for index in source_indexes]
        merged_words = sorted(
            [
                word
                for source_part in source_parts
                for word in source_part.get("word_timestamps", [])
            ],
            key=lambda word: (
                word.get("start", 0.0),
                word.get("end", 0.0),
            ),
        )
        merged_sentence = " ".join(
            str(source_part.get("sentence", "")).strip()
            for source_part in source_parts
            if str(source_part.get("sentence", "")).strip()
        )
        merged_sentence = " ".join(merged_sentence.split())
        target_words = merged_sentence.split()
        part_source = (
            "turn"
            if any(
                source_part.get("part_source") == "turn"
                for source_part in source_parts
            )
            else "sentence"
        )
        turn_ids = {
            source_part.get("turn_id")
            for source_part in source_parts
            if source_part.get("turn_id") is not None
        }
        turn_id = next(iter(turn_ids)) if len(turn_ids) == 1 else None
        turn_break_reason = source_parts[-1].get("turn_break_reason")
        rebuilt_part = _build_reference_part(
            merged_sentence,
            merged_words,
            target_words,
            part_source=part_source,
            turn_id=turn_id,
            turn_break_reason=turn_break_reason,
        )
        if rebuilt_part is None:
            raise ValueError("Failed to rebuild a merged reference part.")
        rebuilt_part["sentence_ko"] = merged_part.translated_text.strip()
        rebuilt_part["source_part_ids"] = merged_part.source_part_ids
        rebuilt_part["vocabulary"] = [
            vocabulary.model_dump() for vocabulary in merged_part.vocabulary
        ]
        merged_payload_parts.append(rebuilt_part)
    return merged_payload_parts


def translate_reference_parts_with_gemini(
    final_script: str,
    sentence_data: list[dict[str, Any]],
) -> TranslationMetadata:
    """Translate and merge reference parts with Gemini.

    Args:
        final_script: Full reference transcript.
        sentence_data: Reference part payload list.

    Returns:
        Translation metadata containing translated text, merged parts, and
        retry information.
    """
    provider = f"gemini:{config.LLM_GEMINI_MODEL}"
    base_parts = [dict(part) for part in sentence_data]
    for part in base_parts:
        part.setdefault("sentence_ko", None)
        part.setdefault("vocabulary", [])

    if not final_script.strip() or not sentence_data:
        return TranslationMetadata(
            final_script_ko=None,
            parts=base_parts,
            learning_expressions=[],
            translation_status="skipped",
            translation_retry_count=0,
            translation_provider=provider,
        )

    if not _get_gemini_api_key():
        logger.warning("Gemini translation skipped: GMS_API_KEY missing")
        return TranslationMetadata(
            final_script_ko=None,
            parts=base_parts,
            learning_expressions=[],
            translation_status="failed",
            translation_retry_count=0,
            translation_provider=provider,
        )

    prompt = _build_prompt(final_script, sentence_data)
    max_retries = max(1, int(config.LLM_GEMINI_MAX_RETRIES))
    retry_count = 0

    for attempt in range(1, max_retries + 1):
        retry_count = attempt
        try:
            raw_response = _request_gemini(prompt)
            (
                full_text_translation,
                parsed_merged_parts,
                parsed_learning_expressions,
            ) = _parse_translation_response(raw_response)
            _validate_merged_part_ids(
                sentence_data,
                parsed_merged_parts,
            )
            merged_parts = _merge_source_parts(
                sentence_data,
                parsed_merged_parts,
            )
            return TranslationMetadata(
                final_script_ko=full_text_translation,
                parts=merged_parts,
                learning_expressions=[
                    expression.model_dump()
                    for expression in parsed_learning_expressions
                ],
                translation_status="success",
                translation_retry_count=retry_count,
                translation_provider=provider,
            )
        except Exception as exc:
            logger.warning(
                "Gemini translation attempt %d/%d failed: %s",
                attempt,
                max_retries,
                exc,
            )
            if attempt >= max_retries or not _is_retryable_exception(exc):
                break
            sleep_sec = config.LLM_GEMINI_RETRY_BACKOFF_SEC * (
                2 ** (attempt - 1)
            )
            time.sleep(sleep_sec)

    return TranslationMetadata(
        final_script_ko=None,
        parts=base_parts,
        learning_expressions=[],
        translation_status="failed",
        translation_retry_count=retry_count,
        translation_provider=provider,
    )
