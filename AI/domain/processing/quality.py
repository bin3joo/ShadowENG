"""
StyleEcho 품질 평가
===================
레퍼런스 오디오 품질 메트릭 추정, 디노이즈 모드 선택,
캡션 정렬 건강도 평가, 종합 reference quality 판정을 담당합니다.
"""

import logging

import numpy as np

import config
from domain.processing.engine_utils import _sum_word_durations
from domain.processing.speaker_analysis import (
    annotate_reference_part_speakers,
)
from domain.processing.text_processing import (
    _first_sentence_has_terminal_punctuation,
)

logger = logging.getLogger(__name__)


def evaluate_caption_alignment_health(
    caption_text: str,
    valid_words: list[dict],
    audio_duration_sec: float,
) -> dict:
    """caption_align 결과의 건강도를 평가하여 fallback 여부를 판단합니다."""
    caption_word_count = max(1, len(caption_text.split()))
    surviving_word_count = len(valid_words)
    surviving_word_ratio = surviving_word_count / caption_word_count
    front_window_words = valid_words[: config.CAPTION_FRONT_WINDOW_WORDS]
    front_low_conf_ratio = 0.0
    if front_window_words:
        low_conf_count = sum(
            1
            for word in front_window_words
            if word.get("score", 1.0) < config.TRIM_FRONT_SCORE
        )
        front_low_conf_ratio = low_conf_count / len(front_window_words)

    leading_gap_sec = (
        float(valid_words[0].get("start", audio_duration_sec))
        if valid_words
        else audio_duration_sec
    )
    trailing_gap_sec = (
        max(
            0.0,
            audio_duration_sec - float(valid_words[-1].get("end", 0.0)),
        )
        if valid_words
        else audio_duration_sec
    )
    first_sentence_has_terminal = _first_sentence_has_terminal_punctuation(
        valid_words,
        config.CAPTION_FRONT_WINDOW_WORDS,
    )

    fallback_reasons: list[str] = []
    if surviving_word_ratio < config.CAPTION_STRONG_MIN_SURVIVING_WORD_RATIO:
        fallback_reasons.append("very_low_surviving_word_ratio")
    elif surviving_word_ratio < config.CAPTION_MIN_SURVIVING_WORD_RATIO:
        fallback_reasons.append("low_surviving_word_ratio")

    if front_low_conf_ratio > config.CAPTION_MAX_FRONT_LOW_CONF_RATIO:
        fallback_reasons.append("high_front_low_conf_ratio")
    if leading_gap_sec > config.CAPTION_MAX_LEADING_GAP_SEC:
        fallback_reasons.append("abnormal_leading_gap")
    if (
        leading_gap_sec > (config.CAPTION_MAX_LEADING_GAP_SEC / 2.0)
        and not first_sentence_has_terminal
    ):
        fallback_reasons.append("incomplete_first_sentence")
    if trailing_gap_sec > config.CAPTION_MAX_LEADING_GAP_SEC:
        fallback_reasons.append("abnormal_trailing_gap")

    should_fallback = False
    if "very_low_surviving_word_ratio" in fallback_reasons:
        should_fallback = True
    elif len(fallback_reasons) >= config.CAPTION_FALLBACK_MIN_REASON_COUNT:
        should_fallback = True

    return {
        "caption_word_count": caption_word_count,
        "caption_surviving_word_count": surviving_word_count,
        "caption_surviving_word_ratio": round(surviving_word_ratio, 3),
        "caption_front_low_conf_ratio": round(front_low_conf_ratio, 3),
        "caption_leading_gap_sec": round(leading_gap_sec, 3),
        "caption_trailing_gap_sec": round(trailing_gap_sec, 3),
        "caption_first_sentence_has_terminal": (first_sentence_has_terminal),
        "caption_fallback_reasons": fallback_reasons,
        "caption_should_fallback": should_fallback,
    }


def estimate_reference_audio_metrics(
    y: np.ndarray,
    sr: int,
    word_timestamps: list[dict],
) -> dict:
    """레퍼런스 suitability 판단용 오디오 품질 지표를 추정합니다."""
    if y.size == 0:
        return {
            "speech_ratio": 0.0,
            "speech_rms": 0.0,
            "noise_rms": 0.0,
            "estimated_snr_db": 0.0,
            "noise_level": "high",
        }

    speech_mask = np.zeros(len(y), dtype=bool)
    for word in word_timestamps:
        start_idx = max(0, int(word.get("start", 0.0) * sr))
        end_idx = min(len(y), int(word.get("end", 0.0) * sr))
        if end_idx > start_idx:
            speech_mask[start_idx:end_idx] = True

    speech_samples = y[speech_mask]
    noise_samples = y[~speech_mask]
    speech_rms = (
        float(np.sqrt(np.mean(np.square(speech_samples))))
        if speech_samples.size
        else 0.0
    )
    noise_rms = (
        float(np.sqrt(np.mean(np.square(noise_samples))))
        if noise_samples.size
        else 0.0
    )
    if noise_rms <= 1e-8:
        noise_rms = max(1e-8, speech_rms * 0.15)

    estimated_snr_db = float(
        20.0 * np.log10(max(speech_rms, 1e-8) / max(noise_rms, 1e-8))
    )
    speech_ratio = float(np.mean(speech_mask)) if speech_mask.size else 0.0

    if (
        estimated_snr_db < config.REFERENCE_HIGH_NOISE_SNR_DB
        or speech_ratio < config.REFERENCE_LOW_SPEECH_RATIO
    ):
        noise_level = "high"
    elif (
        estimated_snr_db < config.REFERENCE_MEDIUM_NOISE_SNR_DB
        or speech_ratio < config.REFERENCE_MEDIUM_SPEECH_RATIO
    ):
        noise_level = "medium"
    else:
        noise_level = "low"

    return {
        "speech_ratio": round(speech_ratio, 3),
        "speech_rms": round(speech_rms, 6),
        "noise_rms": round(noise_rms, 6),
        "estimated_snr_db": round(estimated_snr_db, 2),
        "noise_level": noise_level,
    }


def select_reference_denoise_mode_from_metrics(
    metrics: dict,
) -> str:
    """사전 계산된 메트릭으로 분석용 디노이즈 강도를 선택합니다."""
    if not config.DENOISE_ENABLED:
        return "off"

    configured_mode = config.REFERENCE_DENOISE_MODE.lower()
    if configured_mode in {"off", "mild", "moderate"}:
        return configured_mode

    if metrics["noise_level"] == "high":
        return "moderate"
    if metrics["noise_level"] == "medium":
        return "mild"
    return "off"


def select_reference_denoise_mode(
    y: np.ndarray,
    sr: int,
    word_timestamps: list[dict],
) -> str:
    """레퍼런스 품질에 따라 분석용 디노이즈 강도를 선택합니다."""
    metrics = estimate_reference_audio_metrics(y, sr, word_timestamps)
    return select_reference_denoise_mode_from_metrics(metrics)


def assess_reference_quality(
    y: np.ndarray,
    sr: int,
    word_timestamps: list[dict],
    sentence_data: list[dict],
    caption_source: str,
    stt_method: str,
    denoise_mode: str,
    precomputed_metrics: dict | None = None,
) -> dict:
    """레퍼런스 suitability, speaker risk, dialog risk 메타데이터를 계산합니다."""
    metrics = (
        precomputed_metrics
        if precomputed_metrics is not None
        else estimate_reference_audio_metrics(y, sr, word_timestamps)
    )
    speaker_stats = annotate_reference_part_speakers(sentence_data)
    word_scores = [word.get("score", 0.0) for word in word_timestamps]
    median_alignment = float(np.median(word_scores)) if word_scores else 0.0
    low_alignment_ratio = sum(
        1
        for score in word_scores
        if score < config.REFERENCE_LOW_ALIGNMENT_SCORE
    ) / max(1, len(word_scores))
    gaps = [
        word_timestamps[idx + 1].get("start", 0.0)
        - word_timestamps[idx].get("end", 0.0)
        for idx in range(len(word_timestamps) - 1)
    ]
    overlap_ratio = (
        sum(1 for gap in gaps if gap <= 0.02) / max(1, len(gaps))
        if gaps
        else 0.0
    )
    pause_turns = sum(
        1 for part in sentence_data if part.get("turn_id") is not None
    )
    short_turn_ratio = (
        sum(1 for part in sentence_data if part.get("word_count", 0) <= 4)
        / max(1, len(sentence_data))
        if sentence_data
        else 0.0
    )
    part_f0_medians: list[float] = []
    for part in sentence_data:
        feature_f0 = (
            part.get("features", {}).get("f0_array", [])
            if isinstance(part.get("features"), dict)
            else []
        )
        valid_f0 = [value for value in feature_f0 if value > 0]
        if valid_f0:
            part_f0_medians.append(float(np.median(valid_f0)))

    speaker_shift_count = 0
    for idx in range(1, len(part_f0_medians)):
        prev_median = part_f0_medians[idx - 1]
        curr_median = part_f0_medians[idx]
        if prev_median > 0 and curr_median > 0:
            shift = abs(12.0 * np.log2(curr_median / prev_median))
            if shift >= config.REFERENCE_SPEAKER_SHIFT_SEMITONES:
                speaker_shift_count += 1

    speaker_shift_ratio = (
        speaker_shift_count / max(1, len(part_f0_medians) - 1)
        if len(part_f0_medians) > 1
        else 0.0
    )

    quality_reasons: list[str] = []
    warnings: list[str] = []
    if metrics["noise_level"] == "high":
        quality_reasons.append("high_noise")
    elif metrics["noise_level"] == "medium":
        quality_reasons.append("medium_noise")

    if low_alignment_ratio >= config.REFERENCE_LOW_ALIGNMENT_RATIO:
        quality_reasons.append("low_alignment_confidence")

    if overlap_ratio >= config.REFERENCE_HIGH_OVERLAP_RATIO:
        quality_reasons.append("high_overlap_risk")
    elif overlap_ratio >= config.REFERENCE_MEDIUM_OVERLAP_RATIO:
        quality_reasons.append("medium_overlap_risk")

    if speaker_shift_ratio >= 0.5:
        quality_reasons.append("speaker_instability")

    if pause_turns >= 2 and short_turn_ratio >= 0.4:
        quality_reasons.append("dialog_like")

    diarization_multi_detected = (
        speaker_stats["diarization_used"]
        and speaker_stats["detected_speaker_count"] >= 2
    )
    single_speaker_supported = (
        speaker_stats["diarization_used"]
        and speaker_stats["dominant_speaker_word_ratio"]
        >= config.REFERENCE_SINGLE_SPEAKER_DOMINANT_RATIO
        and speaker_stats["second_speaker_word_ratio"]
        <= config.REFERENCE_SINGLE_SPEAKER_MAX_SECOND_RATIO
        and speaker_stats["speaker_label_change_ratio"]
        <= config.REFERENCE_SINGLE_SPEAKER_MAX_LABEL_CHANGE_RATIO
        and speaker_stats["multi_speaker_part_ratio"]
        <= config.REFERENCE_SINGLE_SPEAKER_MAX_MULTI_PART_RATIO
        and speaker_stats["dominant_speaker_part_ratio"]
        >= config.REFERENCE_SINGLE_SPEAKER_MIN_PART_RATIO
    )
    strong_multi_speaker_evidence = (
        diarization_multi_detected
        and speaker_stats["second_speaker_word_ratio"]
        >= config.REFERENCE_MULTI_SPEAKER_MIN_SECOND_RATIO
        and (
            speaker_stats["speaker_label_change_ratio"]
            >= config.REFERENCE_MULTI_SPEAKER_MIN_LABEL_CHANGE_RATIO
            or speaker_stats["multi_speaker_part_ratio"]
            >= config.REFERENCE_MULTI_SPEAKER_MIN_PART_RATIO
        )
    )

    speaker_mode = "single_speaker_assumed"
    if strong_multi_speaker_evidence:
        quality_reasons.append("multi_speaker_detected")
        speaker_mode = "multi_speaker_suspected"
    elif single_speaker_supported:
        speaker_mode = "single_speaker_assumed"
    elif diarization_multi_detected:
        speaker_mode = "speaker_uncertain"
    elif overlap_ratio >= config.REFERENCE_HIGH_OVERLAP_RATIO:
        speaker_mode = "multi_speaker_suspected"
    elif (
        speaker_shift_ratio > 0.0
        or overlap_ratio >= config.REFERENCE_MEDIUM_OVERLAP_RATIO
    ):
        speaker_mode = "speaker_uncertain"

    dialog_mode = "sentence"
    if any(part.get("part_source") == "turn" for part in sentence_data):
        dialog_mode = "turn_segmented"
    elif pause_turns >= 2 and short_turn_ratio >= 0.4:
        dialog_mode = "dialog_like"

    reference_quality = "good"
    if (
        config.REFERENCE_REJECT_ON_LOW_ALIGNMENT
        and low_alignment_ratio >= config.REFERENCE_LOW_ALIGNMENT_RATIO
    ) or (
        config.REFERENCE_REJECT_ON_HIGH_OVERLAP
        and overlap_ratio >= config.REFERENCE_HIGH_OVERLAP_RATIO
    ):
        reference_quality = "reject"
    elif quality_reasons:
        reference_quality = "risky"

    if reference_quality == "risky":
        warnings.append(
            "Reference quality is risky; use this clip cautiously."
        )
    if dialog_mode == "turn_segmented":
        warnings.append(
            "Dialog-like clip was segmented into pause-based turns."
        )
    if speaker_mode != "single_speaker_assumed":
        warnings.append("Single speaker consistency could not be guaranteed.")

    return {
        "reference_quality": reference_quality,
        "quality_reasons": quality_reasons,
        "warnings": warnings,
        "denoise_mode": denoise_mode,
        "speaker_mode": speaker_mode,
        "dialog_mode": dialog_mode,
        "turn_count": max(
            [part.get("turn_id", 0) or 0 for part in sentence_data],
            default=0,
        ),
        "caption_source": caption_source,
        "stt_method": stt_method,
        "alignment_median_score": round(median_alignment, 3),
        "low_alignment_ratio": round(low_alignment_ratio, 3),
        "overlap_risk_ratio": round(overlap_ratio, 3),
        "speaker_shift_ratio": round(speaker_shift_ratio, 3),
        "estimated_snr_db": metrics["estimated_snr_db"],
        "noise_level": metrics["noise_level"],
        "speech_ratio": metrics["speech_ratio"],
        "diarization_used": speaker_stats["diarization_used"],
        "detected_speaker_count": speaker_stats["detected_speaker_count"],
    }
