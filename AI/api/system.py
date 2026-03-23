"""시스템 헬스체크 및 루트 API 라우트."""

from typing import Any

from fastapi import APIRouter

router = APIRouter()


@router.get("/")
def root() -> dict[str, Any]:
    """루트 경로 접속 시 안내 메시지를 반환합니다.

    Returns:
        환영 메시지 딕셔너리.
    """
    return {
        "message": "Welcome",
    }


@router.get("/health")
def health_check() -> dict[str, Any]:
    """서버 상태 확인용 엔드포인트.

    로드 밸런서나 모니터링 도구가 사용합니다.

    Returns:
        서버 상태 딕셔너리.
    """
    return {
        "status": "healthy",
    }
