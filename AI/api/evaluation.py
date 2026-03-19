"""Audio evaluation API routes."""

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
    """Evaluate a user audio sample against a reference payload."""
    return await run_in_threadpool(
        evaluate_audio_request,
        req,
        background_tasks,
    )
