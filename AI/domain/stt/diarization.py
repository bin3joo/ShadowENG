"""WhisperX Diarization 로드 및 적용."""

import importlib
import logging
import os
from typing import Any

import config
import numpy as np
import whisperx

logger = logging.getLogger(__name__)


def load_diarization_model(
    diarization_device: str,
) -> Any | None:
    """가능한 환경이면 WhisperX diarization 파이프라인을 초기화합니다.

    Args:
        diarization_device: diarization 실행 디바이스.

    Returns:
        Diarization 파이프라인 인스턴스 또는 ``None``.
    """
    if not config.REFERENCE_ENABLE_DIARIZATION:
        return None

    hf_token = os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_TOKEN")
    if not hf_token:
        try:
            from huggingface_hub import get_token

            hf_token = get_token()
        except Exception:
            hf_token = None
    if not hf_token:
        logger.info(
            "HF token 없음 → diarization 비활성화 "
            "(env 또는 huggingface_hub 로그인 필요)"
        )
        return None

    diarization_cls = getattr(whisperx, "DiarizationPipeline", None)
    if diarization_cls is None:
        try:
            diarize_module = importlib.import_module("whisperx.diarize")
            diarization_cls = getattr(
                diarize_module,
                "DiarizationPipeline",
                None,
            )
        except Exception as exc:
            logger.warning("whisperx.diarize import 실패: %s", exc)
            diarization_cls = None
    if diarization_cls is None:
        logger.warning(
            "WhisperX diarization pipeline 을 찾을 수 없습니다."
        )
        return None

    try:
        logger.info("Loading WhisperX diarization pipeline ...")
        try:
            diarization_model = diarization_cls(
                model_name="pyannote/speaker-diarization-3.1",
                use_auth_token=hf_token,
                device=diarization_device,
            )
        except TypeError:
            diarization_model = diarization_cls(
                model_name="pyannote/speaker-diarization-3.1",
                auth_token=hf_token,
                device=diarization_device,
            )
        if diarization_model is None:
            raise RuntimeError(
                "Diarization pipeline 초기화 결과가 None 입니다. "
                "pyannote gated 모델 접근 권한 또는 토큰 설정을 확인하세요."
            )
        return diarization_model
    except Exception as exc:
        logger.warning(
            "Diarization pipeline 로드 실패: %s | "
            "https://hf.co/pyannote/speaker-diarization-3.1 에서 "
            "약관 수락 및 토큰 권한을 확인하세요.",
            exc,
        )
        return None


def apply_diarization(
    audio: np.ndarray,
    result: dict[str, Any],
    diarization_model: Any | None,
) -> tuple[dict[str, Any], bool]:
    """가능한 경우 정렬 결과에 화자 라벨을 부여합니다.

    Args:
        audio: 로드된 오디오 배열.
        result: WhisperX 정렬 결과.
        diarization_model: 로드된 diarization 파이프라인 또는 ``None``.

    Returns:
        화자 라벨이 부여된 결과와 diarization 적용 여부 튜플.
    """
    if diarization_model is None:
        return result, False

    assign_word_speakers = getattr(whisperx, "assign_word_speakers", None)
    if assign_word_speakers is None:
        try:
            diarize_module = importlib.import_module("whisperx.diarize")
            assign_word_speakers = getattr(
                diarize_module,
                "assign_word_speakers",
                None,
            )
        except Exception as exc:
            logger.warning(
                "whisperx.diarize.assign_word_speakers import 실패: %s",
                exc,
            )
            assign_word_speakers = None
    if assign_word_speakers is None:
        logger.warning(
            "WhisperX assign_word_speakers 를 찾을 수 없습니다."
        )
        return result, False

    try:
        diarization_kwargs: dict = {}
        if config.REFERENCE_DIARIZATION_NUM_SPEAKERS is not None:
            diarization_kwargs["num_speakers"] = (
                config.REFERENCE_DIARIZATION_NUM_SPEAKERS
            )
        else:
            if config.REFERENCE_DIARIZATION_MIN_SPEAKERS is not None:
                diarization_kwargs["min_speakers"] = (
                    config.REFERENCE_DIARIZATION_MIN_SPEAKERS
                )
            if config.REFERENCE_DIARIZATION_MAX_SPEAKERS is not None:
                diarization_kwargs["max_speakers"] = (
                    config.REFERENCE_DIARIZATION_MAX_SPEAKERS
                )

        diarize_segments = diarization_model(
            audio,
            **diarization_kwargs,
        )
        diarized_result = assign_word_speakers(diarize_segments, result)
        return diarized_result, True
    except Exception as exc:
        logger.warning("Diarization 적용 실패: %s", exc)
        return result, False
