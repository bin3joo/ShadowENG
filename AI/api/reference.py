"""Reference generation API routes."""

from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool

try:
    from ..schemas import GenerateReferenceRequest, GenerateReferenceResponse
    from ..services.reference_service import generate_reference
except ImportError:
    from schemas import GenerateReferenceRequest, GenerateReferenceResponse
    from services.reference_service import generate_reference

router = APIRouter()


@router.post(
    "/api/v1/generate-reference",
    response_model=GenerateReferenceResponse,
)
async def generate_reference_endpoint(
    req: GenerateReferenceRequest,
    background_tasks: BackgroundTasks,
) -> dict:
    """Generate a StyleEcho reference payload from YouTube input."""
    return await run_in_threadpool(
        generate_reference,
        req,
        background_tasks,
    )
