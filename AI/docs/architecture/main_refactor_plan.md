# Main Refactor Plan

## 목적

`main.py`의 endpoint orchestration 책임을 더 줄이고,
내부 기능과 외부 연동 기능을 명확히 분리하기 위한 기준 문서입니다.

## 현재 `main.py`가 담당하는 역할

### API 레이어 역할

- FastAPI 앱 생성
- lifespan에서 pipeline preload
- request / response model 연결
- HTTPException 반환

### orchestration 역할

- 자막 기반 fast path / Whisper STT fallback 선택
- reference 오디오 다운로드와 임시 디렉토리 관리
- 오디오 로드 및 trim 적용
- prosody feature 추출 흐름 제어
- sentence / turn 분할 호출
- quality / speaker / translation / response 조립 호출

### evaluate 관련 역할

- 입력 오디오 URL / base64 처리
- 임시 파일 생성 및 삭제
- pipeline evaluation 호출
- 실패 응답 포맷 제어

## 분리 대상 책임

### 1. API 레이어

후보 위치:

- `pipe/api/reference.py`
- `pipe/api/evaluation.py`

책임:

- endpoint 선언
- request validation 진입점
- service 호출
- HTTP 레벨 예외 변환

### 2. Service 레이어

후보 위치:

- `pipe/services/reference_service.py`
- `pipe/services/evaluation_service.py`

책임:

- use case 단위 orchestration
- fast / slow path 정책 결정
- quality / translation / response build 호출 순서 제어
- background task 등록 정책 정리

### 3. Domain / Processing 레이어

현재 활용 가능 모듈:

- `audio_processing.py`
- `text_processing.py`
- `speaker_analysis.py`
- `quality.py`
- `reference_service.py`

책임:

- 순수 처리 로직
- part 분석
- 품질 판정
- 텍스트 정제 및 분할

### 4. Integration 레이어

후보 위치:

- `pipe/integrations/youtube_service.py`
- `pipe/integrations/io_utils.py`
- `pipe/integrations/translation.py`

책임:

- YouTube caption / audio fetch
- 외부 URL 오디오 다운로드
- 파일 입출력
- 향후 LLM 번역 provider 연결 지점

## 단계별 권장 순서

### Step 1

- `generate-reference` 흐름을 service 함수 하나로 추출
- `evaluate-audio` 흐름을 service 함수 하나로 추출
- `main.py`는 endpoint와 HTTP 예외 처리만 남기기 시작

### Step 2

- 외부 연동 모듈을 `integrations/` 성격으로 재배치
- 번역 provider 인터페이스를 도입할 위치 확정

### Step 3

- 필요 시 `api/`, `services/`, `integrations/` 폴더 구조로 실제 이동
- README 구조도 동기화

## 리팩토링 현황 (Status)

*   **Step 1~3 완료 (2026-03-16)**: API, Service, Integration, Domain 레이어 분리가 완료되었습니다.
*   **Import 체계 및 코드 표준 통일**: 
    - 프로젝트 전반에서 `try...except ImportError`를 제거하고 **절대 경로(Absolute Import)** 방식으로 통일했습니다.
    - 모든 핵심 함수에 **Google Style Docstring**을 적용하고 반환 타입을 구체화하여 코드 일관성과 가독성을 높였습니다.
    - `OmegaConf`를 도입하여 유연한 설정 관리 체계를 구축했습니다.

## 선행 주의사항 (Precautions)

- **절대 경로 사용**: 모든 내부 모듈 참조 시 `from api import ...`, `import config` 등 최상위 루트를 기준으로 작성해야 합니다.
- **실행 환경**: 서버 또는 테스트 스크립트 실행 시 반드시 `AI/` 디렉터리 내에서 실행(`python main.py` 또는 `python -m test.test_api`)해야 import 오류가 발생하지 않습니다.
- 하위 호환 import 경로를 고려해야 합니다.
- 폴더 이동 시 `README.md`와 관련 문서를 함께 갱신해야 합니다.
- LLM 호출 시점과 payload는 별도 설계 문서로 분리하는 것이 좋습니다.
