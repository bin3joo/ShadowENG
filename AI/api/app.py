"""StyleEcho FastAPI app factory."""

import logging
from contextlib import asynccontextmanager
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path

from fastapi import FastAPI, APIRouter

import config
from api.evaluation import router as evaluation_router
from api.reference import router as reference_router
from api.system import router as system_router
from pipeline import get_pipeline
router = APIRouter()

_LOG_DIR = Path(__file__).resolve().parents[1] / "log"
_LOG_DIR.mkdir(parents=True, exist_ok=True)

_LOG_FORMAT = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"

# 콘솔 핸들러
_stream_handler = logging.StreamHandler()
_stream_handler.setFormatter(logging.Formatter(_LOG_FORMAT))

# 파일 핸들러 (매일 자정 로테이션, 최대 30일 보관)
_file_handler = TimedRotatingFileHandler(
    filename=str(_LOG_DIR / "styleecho.log"),
    when="midnight",
    interval=1,
    backupCount=30,
    encoding="utf-8",
)
_file_handler.setFormatter(logging.Formatter(_LOG_FORMAT))

logging.basicConfig(
    level=logging.INFO,
    handlers=[_stream_handler, _file_handler],
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
    app.include_router(system_router)
    return app

app = create_app()
