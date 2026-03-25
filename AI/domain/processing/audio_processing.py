"""StyleEcho 오디오 처리.

분석용 디노이징(Track B)과 구간 경계 정제(trim) 로직을 담당합니다.
"""

import logging
import os
import re
import threading
from typing import Any

import config
import noisereduce as nr
import numpy as np

logger = logging.getLogger(__name__)

_separator_instance = None
_separator_lock = threading.Lock()


def _update_separator_output_dir(
    separator: Any,
    output_dir: str,
) -> None:
    """Separator 및 내부 model_instance의 output_dir을 동기화합니다.

    audio-separator는 ``load_model()`` 시점에 ``model_instance``에
    ``output_dir`` 복사본을 전달하므로, 외부에서 ``separator.output_dir``
    만 바꾸면 실제 파일 저장 경로가 반영되지 않습니다.
    이 함수는 두 곳을 모두 갱신합니다.

    Args:
        separator: audio-separator ``Separator`` 인스턴스.
        output_dir: 새 출력 디렉터리 경로.
    """
    separator.output_dir = output_dir
    model_inst = getattr(separator, "model_instance", None)
    if model_inst is not None:
        model_inst.output_dir = output_dir


def _get_separator_instance(output_dir: str) -> Any:
    """audio-separator 인스턴스를 하나만 생성하고 모델을 미리 로드하여 재사용합니다.

    첫 호출 시 모델을 GPU/CPU에 올린 뒤 전역 변수에 캐싱하고,
    이후 호출에서는 ``output_dir`` 만 갱신하여 즉시 반환합니다.

    Args:
        output_dir: 분리된 스템 파일 출력 디렉터리.

    Returns:
        모델이 로드된 ``Separator`` 인스턴스.

    Raises:
        ImportError: audio-separator 패키지가 없을 때.
    """
    global _separator_instance
    if _separator_instance is not None:
        _update_separator_output_dir(_separator_instance, output_dir)
        return _separator_instance

    with _separator_lock:
        if _separator_instance is None:
            try:
                from audio_separator.separator import Separator
            except ImportError as e:
                raise ImportError(
                    "audio-separator 패키지 미설치. "
                    "pip install audio-separator[gpu] 로 설치하세요."
                ) from e

            device = config.VR_DEVICE
            if device == "auto":
                import torch
                device = "cuda" if torch.cuda.is_available() else "cpu"

            separator_kwargs = {
                "model_file_dir": os.path.join(
                    os.path.dirname(
                        os.path.dirname(os.path.dirname(__file__))
                    ),
                    "models",
                    "vr",
                ),
                "output_dir": output_dir,
            }

            logger.info(
                "VR 분리 모델 초기 로드 "
                "(이 작업은 한 번만 실행됩니다): %s",
                config.VR_MODEL,
            )
            separator = Separator(**separator_kwargs)
            separator.load_model(model_filename=config.VR_MODEL)
            _separator_instance = separator
            logger.info("VR 분리 모델 로드 완료")

        _update_separator_output_dir(_separator_instance, output_dir)
        return _separator_instance


def denoise_for_analysis(
    y: np.ndarray,
    sr: int,
    profile: str | None = None,
) -> np.ndarray:
    """억양 분석 전용 디노이징을 적용합니다.

    Args:
        y: 입력 오디오 배열.
        sr: ``y`` 의 샘플레이트.
        profile: 디노이즈 강도 프로필 (선택).

    Returns:
        디노이즈된 오디오 배열.
    """
    if not config.DENOISE_ENABLED or profile == "off":
        return y

    stationary = config.DENOISE_STATIONARY
    prop_decrease = config.DENOISE_PROP_DECREASE
    if profile == "mild":
        prop_decrease = min(prop_decrease, 0.55)
    elif profile == "moderate":
        stationary = False
        prop_decrease = max(prop_decrease, 0.88)

    return nr.reduce_noise(
        y=y,
        sr=sr,
        stationary=stationary,
        prop_decrease=prop_decrease,
        n_fft=1024,
        time_mask_smooth_ms=50
    )


def peak_normalize_audio(y: np.ndarray) -> np.ndarray:
    """클리핑 없이 오디오 배열을 피크 정규화합니다.

    Args:
        y: 입력 오디오 배열.

    Returns:
        피크 정규화된 오디오 배열. 무음 입력 시 원본 배열 반환.
    """
    max_amp = np.max(np.abs(y))
    if max_amp > 0:
        return y / max_amp
    return y


def separate_vocals(
    audio_path: str,
    output_dir: str,
) -> str:
    """VR 활성화 시 레퍼런스 오디오에서 보컬을 분리합니다.

    Args:
        audio_path: 소스 WAV 경로.
        output_dir: 분리된 보컬 출력 디렉터리.

    Returns:
        분리된 보컬 WAV 경로. VR 비활성화 또는 분리 실패 시
        원본 경로 반환.
    """

    if not config.VR_ENABLED:
        logger.info("VR disabled, skipping vocal separation")
        return audio_path

    try:
        separator = _get_separator_instance(output_dir)
    except ImportError as e:
        logger.warning(
            "audio-separator 패키지 미설치 또는 의존성 로드 실패: %s. "
            "pip install audio-separator[gpu] 로 설치하세요. "
            "원본 오디오를 사용합니다.",
            e,
        )
        return audio_path
    except Exception as e:
        logger.warning("VR 인스턴스 생성 중 기타 에러 발생: %s", e)
        return audio_path

    try:

        output_files = separator.separate(audio_path)

        # audio-separator는 [vocals, accompaniment] 순서로 반환
        if output_files:
            logger.debug("VR output files: %s", output_files)
            vocal_path = os.path.join(output_dir, output_files[-1])
            if os.path.exists(vocal_path):
                logger.info("Vocal separation 완료: %s", vocal_path)

                # 비보컬 트랙(디버그 스템) 정리
                if not getattr(config, "VR_SAVE_DEBUG_STEMS", False):
                    for stem_file in output_files[:-1]:
                        stem_path = os.path.join(output_dir, stem_file)
                        try:
                            if os.path.exists(stem_path):
                                os.remove(stem_path)
                        except OSError as e:
                            logger.debug(
                                "Failed to remove stem %s: %s", stem_file, e
                            )

                return vocal_path

        logger.warning(
            "VR 출력 파일을 찾을 수 없습니다. 원본 오디오를 사용합니다."
        )
        return audio_path

    except Exception as exc:
        logger.warning("보컬 분리 실패 (fallback to original): %s", exc)
        return audio_path


def trim_boundary_fragments(
    word_timestamps: list[dict[str, Any]],
    full_text: str,
    audio_duration_sec: float,
    front_score_threshold: float = config.TRIM_FRONT_SCORE,
    back_score_threshold: float = config.TRIM_BACK_SCORE,
    boundary_gap_sec: float = config.TRIM_BOUNDARY_GAP,
    min_words: int = config.TRIM_MIN_WORDS,
) -> tuple[list[dict[str, Any]], str]:
    """요청 경계 근처의 불완전한 발화를 정제합니다.

    Args:
        word_timestamps: WhisperX 정렬된 단어 리스트.
        full_text: 전체 정렬된 트랜스크립트.
        audio_duration_sec: 실제 오디오 길이(초).
        front_score_threshold: 전방 경계 저신뢰도 임계값.
        back_score_threshold: 후방 경계 저신뢰도 임계값.
        boundary_gap_sec: 경계 gap 임계값(초).
        min_words: 정제 후 필요한 최소 단어 수.

    Returns:
        정제된 단어 타임스탬프와 정제된 텍스트 튜플.
    """
    if not word_timestamps:
        return [], ""

    trimmed = list(word_timestamps)

    start_idx = 0
    for i, word_info in enumerate(trimmed):
        word_clean = re.sub(r"[^a-zA-Z']", "", word_info["word"])
        is_lowercase = bool(word_clean) and word_clean[0].islower()
        is_low_conf = word_info.get("score", 1.0) < front_score_threshold
        if is_lowercase and is_low_conf:
            start_idx = i + 1
        else:
            break

    trimmed = trimmed[start_idx:]
    if not trimmed:
        return [], ""

    if trimmed and trimmed[0].get("start", 0.0) > max(0.35, boundary_gap_sec):
        extra_start_idx = 0
        for i, word_info in enumerate(trimmed):
            is_low_conf = word_info.get("score", 1.0) < front_score_threshold
            if not is_low_conf:
                break
            extra_start_idx = i + 1

        if extra_start_idx > 0 and len(trimmed[extra_start_idx:]) >= min_words:
            trimmed = trimmed[extra_start_idx:]
            if not trimmed:
                return [], ""

    sentence_end_re = re.compile(r"[.!?][\"']?$")

    last_complete_idx = len(trimmed) - 1

    for i in range(len(trimmed) - 1, -1, -1):
        word_info = trimmed[i]
        last_end = word_info.get("end", audio_duration_sec)
        at_boundary = (audio_duration_sec - last_end) < boundary_gap_sec
        low_conf = word_info.get("score", 1.0) < back_score_threshold
        no_punct = not sentence_end_re.search(word_info["word"].strip())

        if at_boundary and (low_conf or no_punct):
            for j in range(i - 1, -1, -1):
                if sentence_end_re.search(trimmed[j]["word"].strip()):
                    last_complete_idx = j
                    break
            else:
                last_complete_idx = -1
            break

    if last_complete_idx == -1:
        return [], ""

    trimmed = trimmed[: last_complete_idx + 1]

    if len(trimmed) < min_words:
        return [], ""

    refined_text = " ".join(word_info["word"] for word_info in trimmed).strip()
    return trimmed, refined_text
