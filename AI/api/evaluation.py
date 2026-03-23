"""오디오 평가 API 라우트."""

from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool
from schemas import EvaluateAudioRequest, EvaluateAudioResponse
from services.evaluation_service import evaluate_audio_request

router = APIRouter()


@router.post(
    "/api/v1/evaluate-audio",
    response_model=EvaluateAudioResponse,
)
async def evaluate_audio_endpoint(
    background_tasks: BackgroundTasks,
    req: EvaluateAudioRequest,
) -> dict:
    """유저 오디오를 레퍼런스 페이로드 기준으로 평가합니다.

    Args:
        background_tasks: FastAPI 백그라운드 태스크 레지스트리.
        req: 유저 오디오 평가 요청 본문.

    Returns:
        직렬화된 평가 응답 페이로드.
    """
    return await run_in_threadpool(
        evaluate_audio_request,
        req,
        background_tasks,
    )
