"""Use-case service for StyleEcho reference generation."""

import logging
import os
import tempfile
import uuid

import librosa
import numpy as np
from fastapi import BackgroundTasks, HTTPException

try:
    from .. import config
    from ..domain.processing.audio_processing import trim_boundary_fragments
    from ..domain.processing.quality import (
        assess_reference_quality,
        select_reference_denoise_mode,
    )
    from ..domain.processing.speaker_analysis import (
        annotate_reference_part_speakers,
    )
    from ..domain.processing.text_processing import (
        split_into_sentences_with_timestamps,
    )
    from ..integrations.io_utils import (
        export_part_audio,
        persist_reference_audio,
        prepare_reference_audio_dir,
        remove_dir,
        remove_file,
    )
    from ..integrations.youtube_service import (
        download_reference_audio,
        fetch_youtube_captions,
    )
    from ..pipeline import get_pipeline
    from ..schemas import GenerateReferenceRequest
    from .reference_payload import (
        attach_part_analysis,
        build_reference_response,
        sanitize_reference_text,
        sanitize_word_timestamps,
    )
    from .reference_translation_service import (
        translate_reference_parts_with_gemini,
    )
except ImportError:
    import config
    from domain.processing.audio_processing import trim_boundary_fragments
    from domain.processing.quality import (
        assess_reference_quality,
        select_reference_denoise_mode,
    )
    from domain.processing.speaker_analysis import (
        annotate_reference_part_speakers,
    )
    from domain.processing.text_processing import (
        split_into_sentences_with_timestamps,
    )
    from integrations.io_utils import (
        export_part_audio,
        persist_reference_audio,
        prepare_reference_audio_dir,
        remove_dir,
        remove_file,
    )
    from integrations.youtube_service import (
        download_reference_audio,
        fetch_youtube_captions,
    )
    from pipeline import get_pipeline
    from schemas import GenerateReferenceRequest
    from services.reference_payload import (
        attach_part_analysis,
        build_reference_response,
        sanitize_reference_text,
        sanitize_word_timestamps,
    )
    from services.reference_translation_service import (
        translate_reference_parts_with_gemini,
    )

logger = logging.getLogger(__name__)


def _apply_speaker_risk_policy(
    sentence_data: list[dict],
    quality_metadata: dict,
) -> None:
    """Adjust per-part speaker risk according to global speaker mode."""
    if quality_metadata.get("speaker_mode") == "multi_speaker_suspected":
        for part in sentence_data:
            if part.get("speaker_risk") != "high":
                part["speaker_risk"] = "high"
    elif quality_metadata.get("speaker_mode") == "speaker_uncertain":
        for part in sentence_data:
            if part.get("speaker_risk", "low") == "low":
                part["speaker_risk"] = "medium"


def _export_part_audio_files(
    audio_array: np.ndarray,
    sample_rate: int,
    save_dir: str,
    sentence_data: list[dict],
) -> None:
    """Export per-part WAV files for the generated reference."""
    parts_dir = os.path.join(save_dir, "parts")
    for idx, part in enumerate(sentence_data, start=1):
        part_words = part.get("word_timestamps", [])
        word_starts = [
            word.get("start") for word in part_words if "start" in word
        ]
        word_ends = [word.get("end") for word in part_words if "end" in word]
        part_audio_start = (
            max(0.0, min(word_starts) - 0.08)
            if word_starts
            else max(0.0, part["start_sec"] - 0.08)
        )
        part_audio_end = (
            max(part_audio_start, max(word_ends) + 0.12)
            if word_ends
            else max(part_audio_start, part["end_sec"] + 0.12)
        )
        part_audio_filename = f"part_{idx:02d}.wav"
        part_audio_path = os.path.join(parts_dir, part_audio_filename)
        part["audio_path"] = export_part_audio(
            audio_array,
            sample_rate,
            part_audio_start,
            part_audio_end,
            part_audio_path,
        )


def generate_reference(
    req: GenerateReferenceRequest,
    background_tasks: BackgroundTasks,
) -> dict:
    """Generate a reference payload from YouTube captions and audio."""
    tmp_dir: str | None = None
    actual_audio: str | None = None
    full_audio_path: str | None = None
    quality_metadata: dict | None = None
    save_dir: str | None = None

    try:
        pipeline = get_pipeline()
        caption_text, caption_source = fetch_youtube_captions(
            req.video_id,
            req.start_sec,
            req.end_sec,
            padding_sec=config.AUDIO_PADDING_SEC,
        )
        audio_padding_sec = (
            config.AUDIO_PADDING_SEC if caption_source == "manual" else 0.0
        )

        tmp_dir = tempfile.mkdtemp(prefix="styleecho_")
        audio_filename = f"{uuid.uuid4().hex}.wav"
        audio_path = os.path.join(tmp_dir, audio_filename)

        _, actual_audio = download_reference_audio(
            req.video_id,
            req.start_sec,
            req.end_sec,
            audio_path,
            tmp_dir,
            audio_padding_sec,
        )

        if caption_text:
            logger.info(
                "Fast Path: caption-align (source=%s, 자막 %d자)",
                caption_source,
                len(caption_text),
            )
            stats = pipeline.align_text_to_audio(actual_audio, caption_text)
            fallback_reasons = stats.get("caption_fallback_reasons", [])
            should_fallback = config.CAPTION_FALLBACK_ENABLED and (
                stats.get("caption_should_fallback", False)
                or not stats.get("word_timestamps")
            )
            if should_fallback:
                logger.info(
                    "Caption align fallback to Whisper STT: reasons=%s",
                    fallback_reasons or ["empty_alignment"],
                )
                stats = pipeline.extract_whisper_stats(actual_audio)
        else:
            logger.info(
                "Slow Path: full STT transcribe (caption_source=%s)",
                caption_source,
            )
            stats = pipeline.extract_whisper_stats(actual_audio)

        stt_method = stats.get("stt_method", "whisper_stt")
        target_sr = config.TARGET_SR
        audio_array, _ = librosa.load(actual_audio, sr=target_sr)
        audio_duration_sec = float(len(audio_array) / target_sr)

        refined_words, refined_text = trim_boundary_fragments(
            word_timestamps=stats["word_timestamps"],
            full_text=stats["text"],
            audio_duration_sec=audio_duration_sec,
        )
        trimmed_count = len(stats["word_timestamps"]) - len(refined_words)
        final_script = refined_text if refined_text else stats["text"]
        final_words = (
            refined_words if refined_words else stats["word_timestamps"]
        )
        sanitized_words = sanitize_word_timestamps(final_words)
        if sanitized_words:
            final_words = sanitized_words
            final_script = sanitize_reference_text(
                " ".join(word["word"] for word in final_words)
            )
        else:
            final_script = sanitize_reference_text(final_script)
        logger.info(
            "trim_boundary_fragments: removed %d words (%d remain)",
            trimmed_count,
            len(final_words),
        )

        denoise_mode = select_reference_denoise_mode(
            audio_array,
            target_sr,
            final_words,
        )

        start_idx = int(stats["start_time"] * target_sr)
        end_idx = int(stats["end_time"] * target_sr)
        cropped_audio = (
            audio_array[start_idx:end_idx]
            if end_idx > start_idx
            else audio_array
        )

        f0, rms, _ = pipeline.extract_prosody_features(
            cropped_audio,
            target_sr,
            denoise=denoise_mode != "off",
            denoise_profile=denoise_mode,
        )

        sentence_data = split_into_sentences_with_timestamps(
            final_script,
            final_words,
        )
        sentence_data = attach_part_analysis(
            sentence_data,
            f0,
            rms,
            stats["start_time"],
            target_sr,
            config.HOP_LENGTH,
        )

        quality_metadata = assess_reference_quality(
            audio_array,
            target_sr,
            final_words,
            sentence_data,
            caption_source,
            stt_method,
            denoise_mode,
        )
        _apply_speaker_risk_policy(sentence_data, quality_metadata)

        should_reject = quality_metadata.get("reference_quality") == "reject"
        if (
            quality_metadata.get("reference_quality") == "risky"
            and not config.REFERENCE_ALLOW_RISKY
        ):
            should_reject = True

        if should_reject:
            raise HTTPException(
                status_code=422,
                detail={
                    "message": "reference 구간 품질이 낮아 생성이 거부되었습니다.",
                    **quality_metadata,
                },
            )

        translation_result = translate_reference_parts_with_gemini(
            final_script,
            sentence_data,
        )
        sentence_data = attach_part_analysis(
            translation_result.parts,
            f0,
            rms,
            stats["start_time"],
            target_sr,
            config.HOP_LENGTH,
        )
        annotate_reference_part_speakers(sentence_data)
        _apply_speaker_risk_policy(sentence_data, quality_metadata)

        save_dir = prepare_reference_audio_dir(
            req.video_id,
            req.start_sec,
            req.end_sec,
            req.save_dir,
        )
        full_audio_path = persist_reference_audio(actual_audio, save_dir)
        _export_part_audio_files(
            audio_array,
            target_sr,
            save_dir,
            sentence_data,
        )

        background_tasks.add_task(remove_file, actual_audio)
        background_tasks.add_task(remove_dir, tmp_dir)

        return build_reference_response(
            req.video_id,
            req.start_sec,
            req.end_sec,
            final_script,
            stt_method,
            sentence_data,
            trimmed_count,
            final_words,
            quality_metadata,
            full_audio_path,
            translation_result.model_dump(
                include={
                    "final_script_ko",
                    "learning_expressions",
                    "translation_status",
                    "translation_retry_count",
                    "translation_provider",
                }
            ),
        )

    except HTTPException:
        if actual_audio and os.path.exists(actual_audio):
            remove_file(actual_audio)
        if tmp_dir and os.path.exists(tmp_dir):
            remove_dir(tmp_dir)
        if save_dir and os.path.exists(save_dir):
            remove_dir(save_dir)
        raise
    except Exception:
        logger.exception("generate-reference failed")
        if actual_audio and os.path.exists(actual_audio):
            remove_file(actual_audio)
        if tmp_dir and os.path.exists(tmp_dir):
            remove_dir(tmp_dir)
        if save_dir and os.path.exists(save_dir):
            remove_dir(save_dir)
        raise HTTPException(
            status_code=500,
            detail="레퍼런스 생성 중 내부 오류가 발생했습니다.",
        )
