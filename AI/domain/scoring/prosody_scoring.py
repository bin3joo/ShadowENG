"""Prosody scoring with DTW alignment and configurable similarity modes."""

import config
import numpy as np
from fastdtw import fastdtw
from scipy.spatial.distance import euclidean


def _normalize_feature_weights() -> tuple[float, float]:
    total = config.PROSODY_F0_WEIGHT + config.PROSODY_RMS_WEIGHT
    if total <= 0:
        return 0.5, 0.5
    return (
        config.PROSODY_F0_WEIGHT / total,
        config.PROSODY_RMS_WEIGHT / total,
    )


def _pearson_similarity(ref_col: np.ndarray, user_col: np.ndarray) -> float:
    if np.std(ref_col) < 1e-8 or np.std(user_col) < 1e-8:
        return 0.0
    corr = float(np.corrcoef(ref_col, user_col)[0, 1])
    if not np.isfinite(corr):
        return 0.0
    return max(0.0, corr)


def _cosine_similarity(ref_col: np.ndarray, user_col: np.ndarray) -> float:
    denom = float(np.linalg.norm(ref_col) * np.linalg.norm(user_col))
    if denom < 1e-8:
        return 0.0
    sim = float(np.dot(ref_col, user_col) / denom)
    if not np.isfinite(sim):
        return 0.0
    return max(0.0, sim)


def _compute_similarity(ref_col: np.ndarray, user_col: np.ndarray) -> float:
    metric = config.PROSODY_SIMILARITY_METRIC.lower()
    if metric == "cosine":
        return _cosine_similarity(ref_col, user_col)
    return _pearson_similarity(ref_col, user_col)


def _align_features(
    ref_features: np.ndarray, user_features: np.ndarray
) -> tuple[np.ndarray, np.ndarray, float]:
    distance, path = fastdtw(
        ref_features,
        user_features,
        dist=euclidean,
        radius=config.PROSODY_DTW_RADIUS,
    )
    ref_aligned = np.array([ref_features[i] for i, _ in path])
    user_aligned = np.array([user_features[j] for _, j in path])
    normalized_distance = float(distance / max(len(path), 1))
    return ref_aligned, user_aligned, normalized_distance


def _compute_similarity_score(
    ref_aligned: np.ndarray, user_aligned: np.ndarray
) -> float:
    f0_weight, rms_weight = _normalize_feature_weights()
    f0_similarity = _compute_similarity(ref_aligned[:, 0], user_aligned[:, 0])
    rms_similarity = _compute_similarity(ref_aligned[:, 1], user_aligned[:, 1])
    weighted_similarity = (
        f0_similarity * f0_weight + rms_similarity * rms_weight
    )
    return 100.0 * weighted_similarity


def _compute_distance_score(normalized_distance: float) -> float:
    return float(100.0 * np.exp(-config.PROSODY_BETA * normalized_distance))


def analyze_prosody(
    ref_features: np.ndarray,
    user_features: np.ndarray,
) -> float:
    """Compare normalized prosody features and return a 0-100 score."""
    ref_aligned, user_aligned, normalized_distance = _align_features(
        ref_features, user_features
    )
    similarity_score = _compute_similarity_score(
        ref_aligned, user_aligned
    )
    distance_score = _compute_distance_score(normalized_distance)

    mode = config.PROSODY_SCORING_MODE.lower()
    if mode == "distance":
        prosody_score = distance_score
    elif mode == "similarity":
        prosody_score = similarity_score
    else:
        timing_ratio = min(
            max(config.PROSODY_TIMING_PENALTY_WEIGHT, 0.0), 1.0
        )
        timing_penalty = (1.0 - timing_ratio) + timing_ratio * (
            distance_score / 100.0
        )
        prosody_score = similarity_score * timing_penalty

    prosody_score = min(max(float(prosody_score), 0.0), 100.0)
    return round(prosody_score, 1)
