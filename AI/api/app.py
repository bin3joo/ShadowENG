"""StyleEcho FastAPI app factory."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

try:
    from .. import config
    from ..pipeline import get_pipeline
    from .evaluation import router as evaluation_router
    from .reference import router as reference_router
except ImportError:
    import config
    from api.evaluation import router as evaluation_router
    from api.reference import router as reference_router
    from pipeline import get_pipeline

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Preload the WhisperX pipeline during app startup."""
    logger.info(
        "Pre-loading WhisperX pipeline (model=%s, device=%s, compute=%s) ...",
        config.WHISPER_MODEL,
        config.DEVICE,
        config.COMPUTE_TYPE,
    )
    device = None if config.DEVICE == "auto" else config.DEVICE
    get_pipeline(
        whisper_model_size=config.WHISPER_MODEL,
        device=device,
        compute_type=config.COMPUTE_TYPE,
    )
    logger.info("Pipeline ready.")
    yield


def create_app() -> FastAPI:
    """Create and configure the FastAPI application."""
    app = FastAPI(
        title="StyleEcho AI Worker",
        description="유튜브 레퍼런스 자동 생성 및 유저 억양/발음 평가 API",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.include_router(reference_router)
    app.include_router(evaluation_router)
    return app


app = create_app()
