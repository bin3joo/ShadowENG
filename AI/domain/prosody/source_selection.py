"""레퍼런스 F0/RMS 소스 선택 (Original vs VR 게이팅)."""

from typing import Any

import config
import numpy as np


def _build_f0_gate_metrics(f0: np.ndarray) -> dict[str, float]:
    """규칙 기반 소스 게이팅용 간단한 F0 품질 지표를 생성합니다.

    Args:
        f0: F0 특징 배열.

    Returns:
        voiced-ratio 및 jump-ratio 지표를 포함한 딕셔너리.
    """
    if len(f0) == 0:
        return {
            "voiced_ratio": 0.0,
            "jump_ratio": 1.0,
        }

    valid_f0 = f0[f0 > 0]
    voiced_ratio = float(len(valid_f0) / max(len(f0), 1))

    if len(valid_f0) < 2:
        jump_ratio = 1.0 if len(valid_f0) == 1 else 0.0
    else:
        semitone_diff = np.abs(
            12.0 * np.log2(valid_f0[1:] / np.maximum(valid_f0[:-1], 1e-8))
        )
        jump_ratio = float(
            np.mean(
                semitone_diff
                > getattr(config, "REFERENCE_F0_GATE_MAX_SEMITONE_JUMP", 6.0)
            )
        )

    return {
        "voiced_ratio": voiced_ratio,
        "jump_ratio": jump_ratio,
    }


def _build_rms_gate_metrics(rms: np.ndarray) -> dict[str, float]:
    """규칙 기반 소스 게이팅용 간단한 RMS 품질 지표를 생성합니다.

    Args:
        rms: RMS 특징 배열.

    Returns:
        contrast 및 dropout 지표를 포함한 딕셔너리.
    """
    if len(rms) == 0:
        return {
            "contrast_db": 0.0,
            "dropout_ratio": 1.0,
        }

    rms_abs = np.abs(rms.astype(np.float32))
    p10 = float(np.percentile(rms_abs, 10))
    p90 = float(np.percentile(rms_abs, 90))
    contrast_db = float(20.0 * np.log10((p90 + 1e-8) / (p10 + 1e-8)))
    dropout_threshold = p90 * getattr(
        config, "REFERENCE_RMS_GATE_DROPOUT_FRACTION", 0.1
    )
    dropout_ratio = float(np.mean(rms_abs <= dropout_threshold))

    return {
        "contrast_db": contrast_db,
        "dropout_ratio": dropout_ratio,
    }


def select_reference_prosody_sources(
    original_f0: np.ndarray,
    original_rms: np.ndarray,
    vr_f0: np.ndarray,
    vr_rms: np.ndarray,
) -> dict[str, Any]:
    """간단한 게이팅 규칙으로 레퍼런스 F0 및 RMS 트랙을 선택합니다.

    Args:
        original_f0: 원본 오디오에서 추출한 F0.
        original_rms: 원본 오디오에서 추출한 RMS.
        vr_f0: VR 오디오에서 추출한 F0.
        vr_rms: VR 오디오에서 추출한 RMS.

    Returns:
        선택된 억양 배열, 선택된 소스 라벨, 소스별 지표.
    """
    original_f0_metrics = _build_f0_gate_metrics(original_f0)
    vr_f0_metrics = _build_f0_gate_metrics(vr_f0)
    original_rms_metrics = _build_rms_gate_metrics(original_rms)
    vr_rms_metrics = _build_rms_gate_metrics(vr_rms)

    f0_source = "original"
    if (
        original_f0_metrics["voiced_ratio"]
        < getattr(config, "REFERENCE_F0_GATE_MIN_VOICED_RATIO", 0.5)
        and vr_f0_metrics["voiced_ratio"]
        >= original_f0_metrics["voiced_ratio"]
    ):
        f0_source = "vr"
    elif vr_f0_metrics["jump_ratio"] > getattr(
        config, "REFERENCE_F0_GATE_MAX_JUMP_RATIO", 0.2
    ):
        f0_source = "original"
    elif original_f0_metrics["jump_ratio"] > getattr(
        config, "REFERENCE_F0_GATE_MAX_JUMP_RATIO", 0.2
    ) and vr_f0_metrics["voiced_ratio"] >= getattr(
        config, "REFERENCE_F0_GATE_MIN_VOICED_RATIO", 0.5
    ):
        f0_source = "vr"

    rms_source = "original"
    if (
        original_rms_metrics["contrast_db"]
        < getattr(config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0)
        and vr_rms_metrics["contrast_db"]
        >= original_rms_metrics["contrast_db"]
    ):
        rms_source = "vr"
    elif vr_rms_metrics["dropout_ratio"] > getattr(
        config, "REFERENCE_RMS_GATE_MAX_DROPOUT_RATIO", 0.8
    ):
        rms_source = "original"
    elif vr_rms_metrics["contrast_db"] >= getattr(
        config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0
    ) and original_rms_metrics["contrast_db"] < getattr(
        config, "REFERENCE_RMS_GATE_MIN_CONTRAST_DB", 6.0
    ):
        rms_source = "vr"

    return {
        "f0": vr_f0 if f0_source == "vr" else original_f0,
        "rms": vr_rms if rms_source == "vr" else original_rms,
        "f0_source": f0_source,
        "rms_source": rms_source,
        "original_f0_metrics": original_f0_metrics,
        "vr_f0_metrics": vr_f0_metrics,
        "original_rms_metrics": original_rms_metrics,
        "vr_rms_metrics": vr_rms_metrics,
    }
