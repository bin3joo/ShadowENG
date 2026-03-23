"""레퍼런스 생성 API 라우트."""

from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool
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
    """YouTube 입력으로 StyleEcho 레퍼런스 페이로드를 생성합니다.

    Args:
        req: 레퍼런스 생성 요청 본문.
        background_tasks: FastAPI 백그라운드 태스크 레지스트리.

    Returns:
        직렬화된 레퍼런스 응답 페이로드.
    """
    return await run_in_threadpool(
        generate_reference,
        req,
        background_tasks,
    )
