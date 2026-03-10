"""Audio evaluation API routes."""

from fastapi import APIRouter, BackgroundTasks

try:
    from ..schemas import EvaluateAudioRequest, EvaluateAudioResponse
    from ..services.evaluation_service import evaluate_audio_request
except ImportError:
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
    return evaluate_audio_request(req, background_tasks)
