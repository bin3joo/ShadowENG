"""StyleEcho 레퍼런스 생성 유스케이스 서비스."""

import logging
import os
import tempfile
import uuid
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from typing import Any

import config
import librosa
import numpy as np
from domain.processing import audio_processing as audio_processing_module
from domain.processing.quality import (
    assess_reference_quality,
    estimate_reference_audio_metrics,
    select_reference_denoise_mode_from_metrics,
)
from domain.processing.speaker_analysis import annotate_reference_part_speakers
from domain.processing.text_processing import (
    split_into_sentences_with_timestamps,
)
from fastapi import BackgroundTasks, HTTPException
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
from pipeline import get_pipeline, select_reference_prosody_sources
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
    sentence_data: list[dict[str, Any]],
    quality_metadata: dict[str, Any],
) -> None:
    """전역 화자 모드에 따라 파트별 화자 위험도를 조정합니다.

    Args:
        sentence_data: 제자리 수정할 레퍼런스 파트 페이로드 리스트.
        quality_metadata: 레퍼런스 수준 품질 메타데이터.
    """
    if quality_metadata.get("speaker_mode") == "multi_speaker_suspected":
        for part in sentence_data:
            if part.get("speaker_risk") != "high":
                part["speaker_risk"] = "high"
    elif quality_metadata.get("speaker_mode") == "speaker_uncertain":
        for part in sentence_data:
            if part.get("speaker_risk", "low") == "low":
                part["speaker_risk"] = "medium"


def _slice_audio_segment(
    audio_array: np.ndarray,
    sample_rate: int,
    start_sec: float,
    end_sec: float,
) -> np.ndarray:
    """요청 시간 구간으로 오디오 배열을 자릅니다.

    Args:
        audio_array: 소스 오디오 배열.
        sample_rate: ``audio_array`` 의 샘플레이트.
        start_sec: 세그먼트 시작 시간(초).
        end_sec: 세그먼트 종료 시간(초).

    Returns:
        ``np.float32`` 형식의 잘린 오디오 배열.
    """
    start_idx = max(0, int(start_sec * sample_rate))
    end_idx = max(start_idx, int(end_sec * sample_rate))
    return np.asarray(audio_array[start_idx:end_idx], dtype=np.float32)


def _rebase_reference_words(
    words: list[dict[str, Any]],
    offset_sec: float,
    clip_duration_sec: float,
) -> list[dict[str, Any]]:
    """패딩된 단어 타임스탬프를 요청 로컬 타임베이스로 변환합니다.

    Args:
        words: 패딩된 클립 기준 단어 타임스탬프 페이로드.
        offset_sec: 패딩된 클립 내 요청 시작 오프셋(초).
        clip_duration_sec: 요청 클립 길이(초).

    Returns:
        요청 구간에 맞춰 재기준된 단어 타임스탬프 리스트.
    """
    rebased_words: list[dict[str, Any]] = []
    for word in words:
        start = float(word.get("start", 0.0) or 0.0)
        end = float(word.get("end", 0.0) or 0.0)
        local_start = start - offset_sec
        local_end = end - offset_sec
        overlap_start = max(0.0, local_start)
        overlap_end = min(clip_duration_sec, local_end)
        overlap_duration = max(0.0, overlap_end - overlap_start)
        original_duration = max(1e-6, end - start)
        midpoint = (local_start + local_end) / 2.0
        midpoint_in_window = 0.0 <= midpoint <= clip_duration_sec
        overlap_ratio = overlap_duration / original_duration
        if overlap_duration <= 0.0:
            continue
        if (
            not midpoint_in_window
            and overlap_ratio < config.CAPTION_MIN_ENTRY_OVERLAP_RATIO
        ):
            continue
        rebased_word = dict(word)
        rebased_word["start"] = round(overlap_start, 3)
        rebased_word["end"] = round(overlap_end, 3)
        rebased_words.append(rebased_word)
    return rebased_words


def _export_part_audio_files(
    audio_array: np.ndarray,
    sample_rate: int,
    save_dir: str,
    sentence_data: list[dict[str, Any]],
) -> None:
    """생성된 레퍼런스의 파트별 WAV 파일을 내보냅니다.

    Args:
        audio_array: 요청 구간 레퍼런스 오디오 배열.
        sample_rate: ``audio_array`` 의 샘플레이트.
        save_dir: 레퍼런스 아티팩트 출력 디렉터리.
        sentence_data: 오디오 경로가 추가된 레퍼런스 파트 페이로드 리스트.
    """
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
) -> dict[str, Any]:
    """YouTube 자막과 오디오로부터 레퍼런스 페이로드를 생성합니다.

    Args:
        req: 레퍼런스 생성 요청.
        background_tasks: FastAPI 백그라운드 태스크 레지스트리.

    Returns:
        직렬화된 레퍼런스 응답 페이로드.

    Raises:
        HTTPException: 레퍼런스 생성 실패 또는 품질 게이트 거부 시.
    """
    tmp_dir: str | None = None
    actual_audio: str | None = None
    quality_metadata: dict[str, Any] | None = None
    save_dir: str | None = None
    _succeeded = False
    try:
        pipeline = get_pipeline()
        vr_source_mode = config.VR_SOURCE_MODE.lower()
        use_vr = config.VR_ENABLED and vr_source_mode in ("vr", "both")
        tmp_dir = tempfile.mkdtemp(prefix="styleecho_")
        audio_filename = f"{uuid.uuid4().hex}.wav"
        audio_path = os.path.join(tmp_dir, audio_filename)

        download_padding_sec = config.AUDIO_PADDING_SEC
        request_offset_sec = min(req.start_sec, download_padding_sec)
        request_duration_sec = max(0.0, req.end_sec - req.start_sec)

        with ThreadPoolExecutor(max_workers=4) as executor:
            caption_future = executor.submit(
                fetch_youtube_captions,
                req.video_id,
                req.start_sec,
                req.end_sec,
                config.AUDIO_PADDING_SEC,
            )
            download_future = executor.submit(
                download_reference_audio,
                req.video_id,
                req.start_sec,
                req.end_sec,
                audio_path,
                tmp_dir,
                download_padding_sec,
            )

            caption_text, caption_source = caption_future.result()
            _, actual_audio = download_future.result()

            vocal_future = None
            if use_vr:
                vocal_future = executor.submit(
                    audio_processing_module.separate_vocals,
                    actual_audio,
                    tmp_dir,
                )

            if caption_text:
                logger.info(
                    "Fast Path: caption-align (source=%s, 자막 %d자)",
                    caption_source,
                    len(caption_text),
                )
                stats = pipeline.align_text_to_audio(
                    actual_audio, caption_text
                )
                fallback_reasons = stats.get(
                    "caption_fallback_reasons",
                    [],
                )
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

            vocal_audio = actual_audio
            if vocal_future is not None:
                vocal_audio = vocal_future.result()

            stt_method = stats.get("stt_method", "whisper_stt")
            target_sr = config.TARGET_SR
            original_audio_array, _ = librosa.load(
                actual_audio,
                sr=target_sr,
            )
            feature_audio_array, _ = librosa.load(
                vocal_audio,
                sr=target_sr,
            )

            audio_duration_sec = float(len(original_audio_array) / target_sr)

            refined_words, refined_text = (
                audio_processing_module.trim_boundary_fragments(
                    word_timestamps=stats["word_timestamps"],
                    full_text=stats["text"],
                    audio_duration_sec=audio_duration_sec,
                )
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
            request_words = _rebase_reference_words(
                final_words,
                request_offset_sec,
                request_duration_sec,
            )
            if request_words:
                final_words = sanitize_word_timestamps(request_words)
                final_script = sanitize_reference_text(
                    " ".join(word["word"] for word in final_words)
                )
            logger.info(
                "trim_boundary_fragments: removed %d words (%d remain)",
                trimmed_count,
                len(final_words),
            )

            request_audio = _slice_audio_segment(
                original_audio_array,
                target_sr,
                request_offset_sec,
                request_offset_sec + request_duration_sec,
            )
            feature_request_audio = _slice_audio_segment(
                feature_audio_array,
                target_sr,
                request_offset_sec,
                request_offset_sec + request_duration_sec,
            )

            audio_metrics = estimate_reference_audio_metrics(
                request_audio,
                target_sr,
                final_words,
            )
            denoise_mode = select_reference_denoise_mode_from_metrics(
                audio_metrics
            )

            speech_start_sec = (
                float(final_words[0].get("start", 0.0)) if final_words else 0.0
            )
            speech_end_sec = (
                float(final_words[-1].get("end", request_duration_sec))
                if final_words
                else request_duration_sec
            )
            start_idx = int(speech_start_sec * target_sr)
            end_idx = int(speech_end_sec * target_sr)
            cropped_original_feature_audio = (
                request_audio[start_idx:end_idx]
                if end_idx > start_idx
                else request_audio
            )
            cropped_feature_audio = (
                feature_request_audio[start_idx:end_idx]
                if end_idx > start_idx
                else feature_request_audio
            )

            original_prosody_future = None
            if vr_source_mode in ("original", "both"):
                original_prosody_future = executor.submit(
                    pipeline.extract_prosody_features,
                    cropped_original_feature_audio,
                    target_sr,
                    denoise_mode != "off",
                    denoise_mode,
                )

            vr_prosody_future = None
            if use_vr and vr_source_mode in ("vr", "both"):
                vr_prosody_future = executor.submit(
                    pipeline.extract_prosody_features,
                    cropped_feature_audio,
                    target_sr,
                    denoise_mode != "off",
                    denoise_mode,
                )

            f0, rms = None, None

            if vr_source_mode == "original":
                f0, rms, _ = original_prosody_future.result()
                logger.info(
                    "Reference prosody source selected: f0=original "
                    "rms=original (Original only mode)"
                )
            elif vr_source_mode == "vr" and use_vr:
                f0, rms, _ = vr_prosody_future.result()
                logger.info(
                    "Reference prosody source selected: f0=vr "
                    "rms=vr (VR only mode)"
                )
            elif vr_source_mode == "both" and use_vr:
                original_f0, original_rms, _ = original_prosody_future.result()
                vr_f0, vr_rms, _ = vr_prosody_future.result()
                selected_prosody = select_reference_prosody_sources(
                    original_f0,
                    original_rms,
                    vr_f0,
                    vr_rms,
                )
                f0 = selected_prosody["f0"]
                rms = selected_prosody["rms"]
                logger.info(
                    "Reference prosody source selected: f0=%s rms=%s "
                    "(orig_f0=%s vr_f0=%s orig_rms=%s vr_rms=%s)",
                    selected_prosody["f0_source"],
                    selected_prosody["rms_source"],
                    selected_prosody["original_f0_metrics"],
                    selected_prosody["vr_f0_metrics"],
                    selected_prosody["original_rms_metrics"],
                    selected_prosody["vr_rms_metrics"],
                )
            else:
                # 안전장치(예: vr 모드인데 VR이 실패했거나 비활성화된 경우)
                if original_prosody_future:
                    f0, rms, _ = original_prosody_future.result()
                else:
                    original_f0, original_rms, _ = pipeline.extract_prosody_features(
                        cropped_original_feature_audio,
                        target_sr,
                        denoise_mode != "off",
                        denoise_mode,
                    )
                    f0, rms = original_f0, original_rms
                logger.info(
                    "Reference prosody source selected: f0=original "
                    "rms=original (Fallback mode)"
                )

            sentence_data = split_into_sentences_with_timestamps(
                final_script,
                final_words,
            )
            translation_future = executor.submit(
                translate_reference_parts_with_gemini,
                final_script,
                deepcopy(sentence_data),
            )

            sentence_data = attach_part_analysis(
                sentence_data,
                f0,
                rms,
                speech_start_sec,
                target_sr,
                config.HOP_LENGTH,
            )

            quality_metadata = assess_reference_quality(
                request_audio,
                target_sr,
                final_words,
                sentence_data,
                caption_source,
                stt_method,
                denoise_mode,
                precomputed_metrics=audio_metrics,
            )
            _apply_speaker_risk_policy(sentence_data, quality_metadata)

            should_reject = (
                quality_metadata.get("reference_quality") == "reject"
            )
            if (
                quality_metadata.get("reference_quality") == "risky"
                and not config.REFERENCE_ALLOW_RISKY
            ):
                should_reject = True

            if should_reject:
                translation_future.cancel()
                raise HTTPException(
                    status_code=422,
                    detail={
                        "message": "reference 구간 품질이 낮아 생성이 거부되었습니다.",
                        **quality_metadata,
                    },
                )

            translation_result = translation_future.result()
        sentence_data = attach_part_analysis(
            translation_result.parts,
            f0,
            rms,
            speech_start_sec,
            target_sr,
            config.HOP_LENGTH,
        )
        annotate_reference_part_speakers(sentence_data)
        _apply_speaker_risk_policy(sentence_data, quality_metadata)

        # 저장용 오디오만 Peak 정규화 (플레이백 품질 향상)
        if config.SAVE_REFERENCE_AUDIO:
            save_audio = audio_processing_module.peak_normalize_audio(
                request_audio
            )

            prepared_save_dir: str = prepare_reference_audio_dir(
                req.video_id,
                req.start_sec,
                req.end_sec,
                req.save_dir,
            )
            save_dir = prepared_save_dir
            persist_reference_audio(
                save_audio,
                target_sr,
                prepared_save_dir,
            )
            _export_part_audio_files(
                save_audio,
                target_sr,
                prepared_save_dir,
                sentence_data,
            )

        background_tasks.add_task(remove_file, actual_audio)
        background_tasks.add_task(remove_dir, tmp_dir)

        _succeeded = True
        return build_reference_response(
            video_id=req.video_id,
            start_sec=req.start_sec,
            end_sec=req.end_sec,
            final_script=final_script,
            sentence_data=sentence_data,
            trimmed_word_count=trimmed_count,
            final_words=final_words,
            quality_metadata=quality_metadata,
            translation_metadata=translation_result.model_dump(
                include={
                    "final_script_ko",
                    "learning_expressions",
                    "translation_status",
                    "translation_retry_count",
                    "translation_provider",
                }
            ),
            hop_length=config.HOP_LENGTH,
        )

    except HTTPException:
        raise
    except Exception:
        logger.exception("generate-reference failed")
        raise HTTPException(
            status_code=500,
            detail="레퍼런스 생성 중 내부 오류가 발생했습니다.",
        )
    finally:
        if not _succeeded:
            if actual_audio and os.path.exists(actual_audio):
                remove_file(actual_audio)
            if tmp_dir and os.path.exists(tmp_dir):
                remove_dir(tmp_dir)
            if save_dir and os.path.exists(save_dir):
                remove_dir(save_dir)
