# 1. API 파트 (API Layer)

AI 서버의 진입점이자 클라이언트(혹은 메인 백엔드 서버)의 요청을 받아 파이프라인으로 넘겨주는 엔드포인트 계층입니다. **FastAPI** 프레임워크를 기반으로 동작하며, 비동기 처리를 통해 요청을 병렬적으로 수행합니다.

## 구조 요약

*   **`main.py`**: Uvicorn 구동을 위한 최상위 진입점 파일입니다. `api.app.create_app()`을 호출해 FastAPI 애플리케이션 인스턴스를 가져옵니다.
*   **`api/app.py`**: FastAPI 앱 애플리케이션 팩토리입니다. 서버가 시작될 때 `lifespan` 이벤트를 통해 무거운 **WhisperX 모델을 메모리에 Preload(사전 적재)**하여 첫 요청의 지연 시간을 최소화합니다.
*   **`api/reference.py`**: 레퍼런스 생성 라우터 (`/api/v1/generate-reference`)
*   **`api/evaluation.py`**: 유저 오디오 평가 라우터 (`/api/v1/evaluate-audio`)

## 주요 모듈 작동 방식

### 1) Lifespan 매니저 (`api/app.py`)
애플리케이션 구동 시 단 한 번 실행되는 비동기 컨텍스트 매니저입니다.
```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    # WhisperX 모델 (STT 및 Alignment 모델) 사전 로딩
    get_pipeline(config.WHISPER_MODEL, config.DEVICE, config.COMPUTE_TYPE)
    yield
    # 종료 시 리소스 정리 (필요 시)
```
*   **목적:** AI 모델 로딩은 수 초에서 길게는 십수 초가 걸리므로, 클라이언트의 첫 번째 API 호출 응답이 지연되는 콜드 스타트(Cold Start)를 방지합니다.

### 2) 라우터 실행 논리 (`reference.py`, `evaluation.py`)
이 두 라우터는 Pydantic 기반 모델(`schemas.py`)을 통해 들어오는 JSON 요청을 엄격하게 필터링/검증합니다. 모델 추론은 CPU/GPU 연산을 동반하는 CPU-bound 성격의 **동기(Synchronous)** 함수(`generate_reference`, `evaluate_audio_request`)로 작성되어 있습니다.
*   **블로킹 방지 로직:** FastAPI의 이벤트 루프가 Block되는 것을 방지하기 위해 `starlette.concurrency.run_in_threadpool`을 사용하여 백그라운드 스레드 풀에서 AI 파이프라인 함수를 실행합니다.

```python
# api/reference.py 내부 로직 예시
@router.post("/generate-reference", response_model=GenerateReferenceResponse)
async def api_generate_reference(
    req: GenerateReferenceRequest, 
    background_tasks: BackgroundTasks
):
    # I/O 블로킹 및 CPU-bound AI 작업을 안전하게 스레드에서 실행
    result = await run_in_threadpool(generate_reference, req, background_tasks)
    return result
```

### 요약
API 계층은 **라우팅 -> 스키마 검증 -> 스레드 풀 위임 -> BackgroundTasks를 활용한 찌꺼기 파일 정리**라는 매우 간단하지만 안전한 파이프라인 역할을 합니다. 모든 비즈니스와 AI 로직은 하위 `services/` 계층으로 위임됩니다.
