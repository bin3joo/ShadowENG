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
from services.request_trace_service import (
    create_trace_context,
    persist_request_trace,
)
from services.reference_translation_service import (
    translate_reference_parts_with_gemini,
)

logger = logging.getLogger(__name__)


def _summarize_reference_parts(parts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Extract analysis-friendly part features without full contour payloads."""
    return [
        {
            "sentence": part.get("sentence", ""),
            "start_sec": part.get("start_sec"),
            "end_sec": part.get("end_sec"),
            "duration_sec": part.get("duration_sec"),
            "word_count": part.get("word_count"),
            "part_source": part.get("part_source"),
            "turn_id": part.get("turn_id"),
            "turn_break_reason": part.get("turn_break_reason"),
            "speaker_risk": part.get("speaker_risk"),
            "pause_count": part.get("pause_count"),
            "feature_frames": {
                "f0": len(part.get("features", {}).get("f0_array", []))
                if part.get("features")
                else 0,
                "rms": len(part.get("features", {}).get("rms_array", []))
                if part.get("features")
                else 0,
            },
            "source_part_ids": list(part.get("source_part_ids", [])),
        }
        for part in parts
    ]


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


def _transcribe_and_refine(
    pipeline: Any,
    actual_audio: str,
    caption_text: str | None,
    caption_source: str | None,
    original_audio_array: np.ndarray,
    target_sr: int,
    request_offset_sec: float,
    request_duration_sec: float,
) -> tuple[dict[str, Any], str, list[dict[str, Any]], int]:
    """STT 또는 Caption Align 후 단어를 정제·리베이스합니다.

    Args:
        pipeline: ``StyleEchoPipeline`` 인스턴스.
        actual_audio: 다운로드된 오디오 파일 경로.
        caption_text: YouTube 자막 텍스트 (없으면 ``None``).
        caption_source: 자막 출처 라벨.
        original_audio_array: 원본 오디오 배열 (target_sr 기준).
        target_sr: 오디오 샘플레이트.
        request_offset_sec: 패딩된 클립 내 요청 시작 오프셋.
        request_duration_sec: 요청 클립 길이.

    Returns:
        ``(stats, final_script, final_words, trimmed_count)`` 튜플.
    """
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
    final_words = refined_words if refined_words else stats["word_timestamps"]
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
    return stats, final_script, final_words, trimmed_count


def _extract_reference_prosody(
    pipeline: Any,
    executor: ThreadPoolExecutor,
    request_audio: np.ndarray,
    feature_request_audio: np.ndarray,
    final_words: list[dict[str, Any]],
    target_sr: int,
    request_duration_sec: float,
    vr_source_mode: str,
    use_vr: bool,
    denoise_mode: str,
) -> tuple[np.ndarray, np.ndarray, float]:
    """원본/VR 분기에 따라 레퍼런스 F0, RMS를 추출합니다.

    Args:
        pipeline: ``StyleEchoPipeline`` 인스턴스.
        executor: 병렬 작업 실행기.
        request_audio: 요청 구간 원본 오디오 배열.
        feature_request_audio: 요청 구간 VR/원본 특징 오디오 배열.
        final_words: 최종 단어 타임스탬프.
        target_sr: 샘플레이트.
        request_duration_sec: 요청 클립 길이.
        vr_source_mode: VR 소스 모드 (``original`` / ``vr`` / ``both``).
        use_vr: VR 활성화 여부.
        denoise_mode: 디노이즈 프로파일.

    Returns:
        ``(f0, rms, speech_start_sec)`` 튜플.
    """
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
    cropped_original = (
        request_audio[start_idx:end_idx]
        if end_idx > start_idx
        else request_audio
    )
    cropped_feature = (
        feature_request_audio[start_idx:end_idx]
        if end_idx > start_idx
        else feature_request_audio
    )

    original_prosody_future = None
    if vr_source_mode in ("original", "both"):
        original_prosody_future = executor.submit(
            pipeline.extract_prosody_features,
            cropped_original,
            target_sr,
            denoise_mode != "off",
            denoise_mode,
        )

    vr_prosody_future = None
    if use_vr and vr_source_mode in ("vr", "both"):
        vr_prosody_future = executor.submit(
            pipeline.extract_prosody_features,
            cropped_feature,
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
            "Reference prosody source selected: f0=vr " "rms=vr (VR only mode)"
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
        if original_prosody_future:
            f0, rms, _ = original_prosody_future.result()
        else:
            original_f0, original_rms, _ = pipeline.extract_prosody_features(
                cropped_original,
                target_sr,
                denoise_mode != "off",
                denoise_mode,
            )
            f0, rms = original_f0, original_rms
        logger.info(
            "Reference prosody source selected: f0=original "
            "rms=original (Fallback mode)"
        )

    return f0, rms, speech_start_sec


def _assess_reference_and_collect_translation(
    request_audio: np.ndarray,
    target_sr: int,
    final_words: list[dict[str, Any]],
    sentence_data: list[dict[str, Any]],
    translation_future: Any,
    f0: np.ndarray,
    rms: np.ndarray,
    speech_start_sec: float,
    caption_source: str | None,
    stt_method: str,
    denoise_mode: str,
    audio_metrics: dict[str, Any],
) -> tuple[list[dict[str, Any]], dict[str, Any], Any]:
    """품질 평가와 번역을 병렬 실행하고 거부 여부를 판정합니다.

    Args:
        executor: 병렬 작업 실행기.
        request_audio: 요청 구간 오디오 배열.
        target_sr: 샘플레이트.
        final_script: 최종 정제된 트랜스크립트.
        final_words: 최종 단어 타임스탬프.
        f0: F0 특징 배열.
        rms: RMS 특징 배열.
        speech_start_sec: 발화 시작 시간.
        caption_source: 자막 출처 라벨.
        stt_method: STT 방식 라벨.
        denoise_mode: 디노이즈 프로파일.
        audio_metrics: 사전 계산된 오디오 품질 지표.

    Returns:
        ``(sentence_data, quality_metadata, translation_result)`` 튜플.

    Raises:
        HTTPException: 품질 게이트 거부 시.
    """
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

    should_reject = quality_metadata.get("reference_quality") == "reject"
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
    return sentence_data, quality_metadata, translation_result


def _persist_artifacts(
    req: GenerateReferenceRequest,
    request_audio: np.ndarray,
    target_sr: int,
    sentence_data: list[dict[str, Any]],
) -> str | None:
    """레퍼런스 오디오와 파트 WAV 파일을 저장합니다.

    Args:
        req: 레퍼런스 생성 요청.
        request_audio: 요청 구간 오디오 배열.
        target_sr: 샘플레이트.
        sentence_data: 파트 페이로드 리스트.

    Returns:
        저장 디렉터리 경로 또는 ``None``.
    """
    if not config.SAVE_REFERENCE_AUDIO:
        return None

    save_audio = audio_processing_module.peak_normalize_audio(request_audio)
    prepared_save_dir: str = prepare_reference_audio_dir(
        req.video_id,
        req.start_sec,
        req.end_sec,
        req.save_dir,
    )
    persist_reference_audio(save_audio, target_sr, prepared_save_dir)
    _export_part_audio_files(
        save_audio,
        target_sr,
        prepared_save_dir,
        sentence_data,
    )
    return prepared_save_dir


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
    save_dir: str | None = None
    _succeeded = False
    trace_context = create_trace_context("/api/v1/generate-reference")
    trace_intermediate: dict[str, Any] = {
        "request_summary": {
            "video_id": req.video_id,
            "start_sec": req.start_sec,
            "end_sec": req.end_sec,
            "save_dir": req.save_dir,
        }
    }
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
        trace_intermediate["request_audio_window"] = {
            "download_padding_sec": download_padding_sec,
            "request_offset_sec": request_offset_sec,
            "request_duration_sec": request_duration_sec,
            "use_vr": use_vr,
            "vr_source_mode": vr_source_mode,
        }

        target_sr = config.TARGET_SR

        with ThreadPoolExecutor(max_workers=4) as executor:
            # ── 1단계: 다운로드 + 캡션 가져오기 ──
            caption_future = None
            if config.YOUTUBE_CAPTION_ENABLED:
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

            if caption_future is not None:
                caption_text, caption_source = caption_future.result()
            else:
                caption_text, caption_source = None, "disabled"
            _, actual_audio = download_future.result()
            trace_intermediate["download"] = {
                "caption_source": caption_source,
                "audio_path": actual_audio,
            }

            vocal_future = None
            if use_vr:
                vocal_future = executor.submit(
                    audio_processing_module.separate_vocals,
                    actual_audio,
                    tmp_dir,
                )

            # ── 2단계: STT / 정렬 + 단어 정제 ──
            original_audio_array, _ = librosa.load(
                actual_audio,
                sr=target_sr,
            )

            stats, final_script, final_words, trimmed_count = (
                _transcribe_and_refine(
                    pipeline,
                    actual_audio,
                    caption_text,
                    caption_source,
                    original_audio_array,
                    target_sr,
                    request_offset_sec,
                    request_duration_sec,
                )
            )
            sentence_data = split_into_sentences_with_timestamps(
                final_script,
                final_words,
            )
            trace_intermediate["transcription"] = {
                "stt_method": stats.get("stt_method", "whisper_stt"),
                "trimmed_word_count": trimmed_count,
                "final_word_count": len(final_words),
                "final_script_word_count": len(final_script.split()),
                "initial_parts": _summarize_reference_parts(sentence_data),
            }
            translation_future = executor.submit(
                translate_reference_parts_with_gemini,
                final_script,
                deepcopy(sentence_data),
            )

            vocal_audio = actual_audio
            if vocal_future is not None:
                vocal_audio = vocal_future.result()

            stt_method = stats.get("stt_method", "whisper_stt")
            feature_audio_array, _ = librosa.load(
                vocal_audio,
                sr=target_sr,
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
            trace_intermediate["audio_metrics"] = audio_metrics
            trace_intermediate["denoise_mode"] = denoise_mode

            # ── 3단계: Prosody 추출 ──
            f0, rms, speech_start_sec = _extract_reference_prosody(
                pipeline,
                executor,
                request_audio,
                feature_request_audio,
                final_words,
                target_sr,
                request_duration_sec,
                vr_source_mode,
                use_vr,
                denoise_mode,
            )
            trace_intermediate["prosody_summary"] = {
                "f0_frames": len(f0),
                "rms_frames": len(rms),
                "speech_start_sec": speech_start_sec,
                "target_sr": target_sr,
                "hop_length": config.HOP_LENGTH,
            }

            # ── 4단계: 품질 평가 + 번역 ──
            sentence_data, quality_metadata, translation_result = (
                _assess_reference_and_collect_translation(
                    request_audio,
                    target_sr,
                    final_words,
                    sentence_data,
                    translation_future,
                    f0,
                    rms,
                    speech_start_sec,
                    caption_source,
                    stt_method,
                    denoise_mode,
                    audio_metrics,
                )
            )
            trace_intermediate["quality_metadata"] = quality_metadata
            trace_intermediate["translation_metadata"] = (
                translation_result.model_dump(
                    include={
                        "translation_status",
                        "translation_retry_count",
                        "translation_provider",
                        "final_script_ko",
                        "learning_expressions",
                    }
                )
            )

        # ── 5단계: 번역 결과 병합 + 저장 ──
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

        save_dir = _persist_artifacts(
            req,
            request_audio,
            target_sr,
            sentence_data,
        )
        trace_intermediate["final_parts"] = _summarize_reference_parts(
            sentence_data
        )
        trace_intermediate["artifact_dir"] = save_dir

        background_tasks.add_task(remove_file, actual_audio)
        background_tasks.add_task(remove_dir, tmp_dir)

        _succeeded = True
        response_payload = build_reference_response(
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
        persist_request_trace(
            trace_context=trace_context,
            request_payload=trace_intermediate["request_summary"],
            intermediate=trace_intermediate,
            response_payload=response_payload,
        )
        return response_payload

    except HTTPException as exc:
        persist_request_trace(
            trace_context=trace_context,
            request_payload=trace_intermediate["request_summary"],
            intermediate=trace_intermediate,
            error_payload={
                "status_code": exc.status_code,
                "detail": exc.detail,
            },
        )
        raise
    except Exception:
        logger.exception("generate-reference failed")
        persist_request_trace(
            trace_context=trace_context,
            request_payload=trace_intermediate["request_summary"],
            intermediate=trace_intermediate,
            error_payload={
                "status_code": 500,
                "detail": "generate-reference failed",
            },
        )
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
