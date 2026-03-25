"""Prosody feature extraction for F0 and RMS."""

import config
import librosa
import numpy as np
from domain.processing.audio_processing import denoise_for_analysis
from scipy.signal import medfilt


def _normalize_f0(f0: np.ndarray) -> np.ndarray:
    """Normalize F0 with z-score over voiced frames only, forcing unvoiced to 0."""
    # 30Hz 이하는 사실상 무성음으로 간주 (또는 pyin의 0 값)
    unvoiced_mask = f0 < 30.0
    valid_f0 = f0[~unvoiced_mask]
    
    if len(valid_f0) > 0:
        mean_val = np.mean(valid_f0)
        std_val = np.std(valid_f0) + 1e-8
        normalized = (f0 - mean_val) / std_val
        normalized[unvoiced_mask] = 0.0
        return normalized
    return np.zeros_like(f0)


def _normalize_rms(rms: np.ndarray) -> np.ndarray:
    """Normalize RMS with z-score, forcing silent frames to 0."""
    # 하위 15% 또는 극소 신호를 침묵으로 간주
    threshold = max(np.percentile(rms, 15), 1e-4)
    silent_mask = rms <= threshold
    
    mean_val = np.mean(rms)
    std_val = np.std(rms) + 1e-8
    normalized = (rms - mean_val) / std_val
    normalized[silent_mask] = 0.0
    return normalized


def _sanitize_median_kernel(kernel_size: int) -> int:
    kernel = max(1, int(kernel_size))
    if kernel % 2 == 0:
        kernel += 1
    return kernel


def _apply_voiced_mask(
    f0: np.ndarray, voiced_flag: np.ndarray | None
) -> np.ndarray:
    if not config.PROSODY_F0_VOICED_MASK_ENABLED or voiced_flag is None:
        return f0
    voiced_mask = np.asarray(voiced_flag, dtype=bool)[: len(f0)]
    masked_f0 = np.zeros_like(f0)
    masked_f0[voiced_mask] = f0[voiced_mask]
    return masked_f0


def _apply_f0_median_filter(
    f0: np.ndarray, voiced_flag: np.ndarray | None
) -> np.ndarray:
    if not config.PROSODY_F0_MEDIAN_FILTER_ENABLED:
        return f0

    kernel = _sanitize_median_kernel(config.PROSODY_F0_MEDIAN_FILTER_KERNEL)
    if kernel <= 1 or len(f0) < kernel:
        return f0

    filtered = np.array(f0, copy=True)
    if voiced_flag is not None:
        voiced_mask = np.asarray(voiced_flag, dtype=bool)[: len(f0)]
    else:
        voiced_mask = filtered > 0

    voiced_indices = np.flatnonzero(voiced_mask)
    if len(voiced_indices) == 0:
        return filtered

    split_points = np.where(np.diff(voiced_indices) > 1)[0] + 1
    for segment in np.split(voiced_indices, split_points):
        if len(segment) < kernel:
            continue
        filtered[segment] = medfilt(filtered[segment], kernel_size=kernel)
    return filtered


def extract_prosody_features(
    y: np.ndarray,
    sr: int,
    denoise: bool = False,
    denoise_profile: str | None = None,
    hop_length: int | None = None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Extract raw F0/RMS and normalized feature pairs."""
    y_analysis = (
        denoise_for_analysis(y, sr, profile=denoise_profile)
        if denoise
        else y
    )
    if hop_length is None:
        hop_length = config.HOP_LENGTH

    rms = librosa.feature.rms(
        y=y_analysis,
        hop_length=hop_length,
    )[0]

    f0, voiced_flag, _ = librosa.pyin(
        y_analysis,
        sr=sr,
        fmin=librosa.note_to_hz("C2"),
        fmax=librosa.note_to_hz("C7"),
        hop_length=hop_length,
    )
    f0 = np.nan_to_num(f0)
    voiced_flag = (
        np.asarray(voiced_flag, dtype=bool)
        if voiced_flag is not None
        else None
    )

    min_len = min(len(f0), len(rms))
    f0 = f0[:min_len]
    rms = rms[:min_len]
    if voiced_flag is not None:
        voiced_flag = voiced_flag[:min_len]

    f0 = _apply_voiced_mask(f0, voiced_flag)
    f0 = _apply_f0_median_filter(f0, voiced_flag)

    rms_norm = _normalize_rms(rms)
    f0_norm = _normalize_f0(f0)

    features = np.stack([f0_norm, rms_norm], axis=-1)
    return f0, rms, features
