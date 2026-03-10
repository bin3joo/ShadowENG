# StyleEcho 코드 리뷰 및 추가 개선 제안
 
 > 날짜: 2026-03-06  
 > 대상: `pipe/` 전체 (`main.py`, `engine.py`, `config.py`, `constants.py`)  
 > 기준 문서: `code_review_2026-03-05.md` 후속 리뷰
 > 후속 반영: 2026-03-09 기준 `video_id` 계약, 단순 분리 구조, `pass_fail` 반환 정책 적용됨 ✅ 수정됨
 
---

## 0. 리뷰 요약

2026-03-05 리뷰에서 제안된 주요 버그/성능/스코어링 이슈는 대부분 반영되었습니다.  
이번 문서는 **현재 코드 기준으로 아직 남아있는 잠재 이슈**, **구조 개선 포인트**, **리팩토링 후보**, 그리고 **백엔드 연동 변경 사항(YouTube 전체 URL 대신 video ID 전달)**에 대한 구체적인 구현 방안을 정리합니다.

핵심 요약은 다음과 같습니다.

- 기존 주요 품질 이슈는 대부분 해결됨
- 다만 API 계약과 실제 구현 간 **입력 포맷 불일치**가 일부 남아 있음
- `main.py` 가 아직도 **API 레이어 + 외부 연동 + 데이터 가공 책임**을 동시에 수행하고 있음
- `engine.py` 의 private helper 를 `main.py` 에서 직접 import 하는 구조는 결합도가 높음
- YouTube 입력은 향후 `youtube_url` 이 아니라 `video_id` 중심으로 계약을 바꾸는 것이 적절함

---

## 1. 현재 발생 가능 문제점

### CUR-01. `evaluate-audio` 요청 설명과 실제 구현의 `s3://` 지원 범위 불일치
**위치:** `main.py` line 140-146, 233-244, 576-579  
**내용:**
- `EvaluateAudioRequest` 설명에는 `s3://...` URL 지원이라고 명시되어 있음
- 그러나 실제 다운로드 함수 `_download_audio_from_url()` 는 `http/https` 만 허용함
- `evaluate_audio()` 에서는 `audio_str.startswith(("http://", "https://", "s3://"))` 조건으로 분기하므로 `s3://` 입력이 들어오면 다운로드 단계에서 400 에러가 발생함

**영향:**
- 백엔드/프론트가 문서만 보고 `s3://` 를 전달하면 즉시 실패
- 운영 중 인터페이스 오해로 장애가 발생할 수 있음

**개선안:**
- 단기: API 명세와 주석에서 `s3://` 지원 문구 제거
- 중기: presigned URL 만 받도록 계약 통일
- 장기: 정말 `s3://` 가 필요하면 `boto3` 기반 다운로드를 별도 구현

**심각도:** 중간

---

### CUR-02. Pydantic 모델에서 mutable default 직접 사용
**위치:** `main.py` line 111-116, 149, 199  
**내용:**
- `reductions: list[dict] = []`
- `key_expressions: list[str] = []`
- `word_timestamps: list[WordTimestamp] = []`
- `pitch_contour_feedback: list[PitchContourFeedback] = []`

현재 Pydantic 이 내부적으로 방어해 주는 경우가 많더라도, Python/Pydantic 코드 컨벤션상 mutable default 를 직접 두는 패턴은 유지보수상 좋지 않습니다.

**영향:**
- 모델 동작 이해 비용 증가
- 추후 dataclass/plain class 전환 시 버그 유발 가능
- 정적 분석기/리뷰어가 반복적으로 경고를 제기할 가능성 높음

**개선안:**
- `Field(default_factory=list)` 로 전환
- dict 필드도 가능한 한 구체 타입 모델로 승격

**심각도:** 낮음

---

### CUR-03. `main.py` 가 private helper 에 의존
**위치:** `main.py` line 29-34, 498-500 / `engine.py` line 55-68  
**내용:**
- `main.py` 가 `_count_pauses_from_words` 를 직접 import 하여 사용 중
- 밑줄 prefix 함수는 원래 모듈 내부 구현 세부사항이라는 의미를 가짐
- API 레이어가 엔진 내부 유틸 구현에 직접 의존하면서 결합도가 높아짐

**영향:**
- 향후 `engine.py` 리팩토링 시 외부 import 파손 가능
- public API / internal API 경계가 불명확해짐

**개선안:**
- `_count_pauses_from_words` 를 `count_pauses_from_words` 로 승격
- 또는 `utils.py` 로 분리하여 공용 유틸로 명시
- 더 나아가 `split_into_sentences_with_timestamps()` 단계에서 `pause_count` 계산까지 포함해 한 번에 반환하도록 책임 정리

**심각도:** 중간

---

### CUR-04. `main.py` 의 책임 과다
**위치:** `main.py` 전반  
**내용:**
현재 `main.py` 는 다음 역할을 모두 수행합니다.

- FastAPI 앱 초기화
- 요청/응답 모델 정의
- 파일/디렉토리 정리 유틸
- URL 다운로드
- YouTube caption fetch
- yt-dlp 호출
- 오디오 슬라이싱 후 feature 분배
- 번역 호출 orchestration
- response assembly

이는 API 레이어 파일로서는 다소 많은 책임입니다.

**영향:**
- 엔드포인트 변경 시 영향 범위가 큼
- 테스트 단위 분리가 어려움
- 향후 `video_id` 계약 변경 시 수정 포인트가 넓어짐

**개선안:**
- `main.py`: 라우팅 + 스키마 + 예외 변환만 담당
- `youtube_service.py`: caption fetch / yt-dlp command / ID 관련 로직
- `reference_service.py`: generate-reference orchestration
- `storage.py` 또는 `io_utils.py`: temp file / URL download 정리

**심각도:** 중간

---

### CUR-05. 예외 처리 범위가 넓어 장애 원인 분류가 어려움
**위치:** `main.py` line 354-355, 526-530, 607-611 / `engine.py` line 1109-1123  
**내용:**
여러 구간에서 `except Exception as exc:` 로 광범위하게 예외를 처리하고 있습니다.

이 패턴은 API 안정성에는 도움이 되지만, 아래 문제가 있습니다.

- 외부 API 실패 / 입력 오류 / 라이브러리 버그 / 모델 로드 실패가 한 카테고리로 뭉침
- 장애 대응 시 원인별 alert 분리가 어려움
- 클라이언트에게 지나치게 일반적인 에러만 반환될 가능성 있음

**개선안:**
- 예외를 최소 3개 레벨로 분류
  - 입력 검증 오류
  - 외부 의존성 오류 (`yt-dlp`, `youtube-transcript-api`, 번역 모델)
  - 내부 처리 오류
- 내부 로깅에는 `video_id`, `start_sec`, `end_sec`, `stt_method` 등을 포함
- 필요 시 커스텀 예외 (`ReferenceGenerationError`, `CaptionFetchError`) 도입

**심각도:** 낮음 ~ 중간

---

### CUR-06. `generate-reference` 응답 최상위에 section-level pause 정보는 아직 없음
**위치:** `main.py` line 119-128, 512-521  
**내용:**
현재 `parts[].pause_count` 는 추가되었지만, top-level reference 전체의 `pause_count` 또는 `active_speech_sec` 는 응답에 포함되지 않습니다.

**영향:**
- 백엔드가 part 단위가 아니라 전체 구간 단위 캐시를 만들 때 재계산 필요
- evaluate 단계에서 part 외 section 단위 확장 평가를 하려면 추가 가공 필요

**개선안:**
- `GenerateReferenceResponse` 에 아래 필드 추가 검토
  - `pause_count`
  - `active_speech_sec`
  - `word_count`
- 단, 현재 제품이 part 단위 평가 중심이면 우선순위는 낮음

**심각도:** 낮음

---

## 2. 개선점 제안

### IMP-01. Pydantic 스키마를 더 명시적으로 강화
**제안 포인트:**
- `youtube_url: str` 같은 필드는 현재 자유 문자열
- 시간 필드도 음수/역전 범위 검증이 없음
- list/dict 필드 일부는 구조가 느슨함

**개선 방향:**
- `start_sec >= 0`, `end_sec > start_sec` 검증 추가
- `user_audio_format` 허용 목록 제한
- `WordTimestamp.score` 범위 검증
- 자유 `dict` 대신 세부 `BaseModel` 사용 확대

**기대 효과:**
- API 입력 품질 향상
- FastAPI 문서 신뢰도 향상
- 런타임 예외 감소

---

### IMP-02. 캡션 Fast Path / STT Slow Path 의 관측성 강화
**위치:** `main.py` line 417-432  
**내용:**
현재 `stt_method` 는 반환되지만, 왜 caption path 가 실패했는지는 구조화되어 있지 않습니다.

**개선 방향:**
- `caption_status`: `manual`, `auto`, `missing`, `fetch_error`
- `reference_source`: `caption_align` / `whisper_stt`
- fallback 사유를 로깅 및 메트릭으로 분리

**기대 효과:**
- 운영 중 caption 품질 추적 가능
- 느린 요청 원인 분석 가능

---

### IMP-03. 외부 프로세스 호출을 서비스 함수로 캡슐화
**위치:** `main.py` line 382-397  
**내용:**
`yt-dlp` 명령 조립과 실행이 엔드포인트 내부에 직접 존재합니다.

**개선 방향:**
- `build_yt_dlp_command(...)`
- `download_reference_audio(...)`
- 실패 시 표준화된 예외 발생

**기대 효과:**
- 테스트 용이성 증가
- 향후 `video_id` 기반 변경 시 수정 범위 축소

---

## 3. 리팩토링 가능한 지점

### REF-01. `main.py` 스키마와 서비스 로직 분리
**권장 구조:**
```text
pipe/
├── main.py               # FastAPI 라우터/앱 초기화
├── schemas.py            # Pydantic Request/Response 모델
├── reference_service.py  # generate-reference orchestration
├── youtube_service.py    # caption fetch, yt-dlp command, video_id 처리
├── io_utils.py           # temp file, remote audio download
├── engine.py             # STT/정렬/채점 핵심 로직
├── config.py
├── constants.py
└── config_default.yaml
```

**리팩토링 기준:**
- 현재도 충분히 분리 가치가 있음
- 특히 백엔드 연동 계약 변경이 예정되어 있으므로 지금 구조 분리는 효과가 큼

**설계 담당자 제안**
- main.py 이외의 파일들은 폴더 생성 후 기능별로 관리
- 각 폴더별로 역할을 명확히 하여 유지보수성 향상
- AI 피드백 요청
ex) 
```mermaid
pipe/
├── main.py               # FastAPI 라우터/앱 초기화
├── api/                  # API 엔드포인트 정의
│   ├── __init__.py
│   └── reference.py      # generate-reference 엔드포인트
├── utils/                # 유틸리티 함수
│   ├── __init__.py
│   └── io_utils.py       # temp file, remote audio download
├── configs/              # 설정 파일
│   ├── __init__.py
│   ├── config.py         # 설정 로드/검증
│   ├── constants.py      # 상수 정의
│   ├── config.yaml       # 사용자 설정 파일
│   └── config_default.yaml # 기본 설정 및 예시
├── services/             # 비즈니스 로직
│   ├── __init__.py
│   ├── youtube_service.py    # caption fetch, yt-dlp command, video_id 처리
│   ├── engine.py             # STT/정렬/채점 핵심 로직
│   └── reference_service.py  # generate-reference orchestration
├── schemas/              # Pydantic Request/Response 모델 (폴더명 or models)
│   ├── __init__.py
│   └── schemas.py
```

---

### REF-02. sentence 데이터 계산 책임 응집
**현재:**
- `split_into_sentences_with_timestamps()` 가 문장 메타데이터를 계산
- `main.py` 에서 별도로 feature 슬라이싱
- `main.py` 에서 별도로 `pause_count` 계산

**개선 방향:**
- sentence 데이터 후처리를 하나의 service 함수로 통합
- 예: `build_sentence_parts(...) -> list[dict]`

**기대 효과:**
- part 스키마 변경 시 수정 포인트 감소
- `pause_count`, `features`, `sentence_ko` 추가/변경이 쉬워짐

---

### REF-03. private/public 함수 경계 재정의
**대상:**
- `_count_pauses_from_words`
- `_fetch_youtube_captions`
- `_download_audio_from_url`
- `_extract_whisper_stats`

**개선 방향:**
- 외부에서 호출될 함수는 밑줄 제거 후 public API 로 승격
- private helper 는 해당 모듈 내부에서만 사용하도록 유지

**기대 효과:**
- 모듈 책임과 경계가 더 명확해짐
- IDE 자동완성 및 문서화 품질 향상

---

## 4. 추가 변경점 리뷰: YouTube 전체 URL 대신 `video_id` 전달

> 백엔드 변경 사항: 앞으로 Python AI Worker 에는 YouTube 전체 URL 이 아니라 `video_id` 만 전달될 예정

이번 항목은 **즉시 코드 변경 대상이 아니라, 구체적 구현 방안 설계**를 문서화한 것입니다.

### CHANGE-01. 왜 `video_id` 전환이 필요한가

현재 구현은 `youtube_url` 을 받아 아래 작업을 수행합니다.

- `_fetch_youtube_captions()` 내부에서 URL 파싱 후 `video_id` 추출
- `yt-dlp` 에는 원본 URL 전달
- 로그와 응답에도 `youtube_url` 사용

이 구조의 문제는 다음과 같습니다.

- 입력 포맷 다양성(`youtu.be`, `youtube.com/watch`, `shorts`)을 Python 서버가 모두 책임짐
- URL 정규화 책임이 백엔드/프론트가 아니라 AI Worker 로 흘러옴
- API 계약이 불필요하게 넓음
- `video_id` 만 있으면 충분한 곳에서도 전체 URL 을 계속 보관/전달하게 됨

따라서 **백엔드에서 video ID 를 canonical input 으로 정규화한 뒤 Python 서버에 전달하는 방향이 적절**합니다.

**설계 담당자 제안**
변경 후 구조

- video_id : YouTube video ID (ex: abc123xyz89)
- start_sec : 시작 시간 (초)
- end_sec : 종료 시간 (초)

파싱용 URL (변경)

- base_url : YouTube URL (ex: https://www.youtube.com/watch?v={video_id})

---

### CHANGE-02. 권장 API 계약 변경안

#### 기존
```json
{
  "youtube_url": "https://www.youtube.com/watch?v=abc123xyz89",
  "start_sec": 10.0,
  "end_sec": 25.0
}
```

#### 권장 변경
```json
{
  "video_id": "abc123xyz89",
  "start_sec": 10.0,
  "end_sec": 25.0
}
```

#### 선택적 확장안 (id기준으로 백엔드에서 처리할 예정으로 url 반환은 당장 불필요)
```json
{
  "video_id": "abc123xyz89",
  "start_sec": 10.0,
  "end_sec": 25.0,
  "youtube_url": "https://www.youtube.com/watch?v={video_id}"
}
```

- 단기 호환 기간에는 `video_id` 필수 + `youtube_url` 선택으로 운영 가능
- 최종적으로는 `video_id` 단일 입력으로 정리하는 것이 바람직

---

### CHANGE-03. Python 서버 내부 구현 방안

#### 1) 요청 모델 변경
- `GenerateReferenceRequest.youtube_url` 를 `video_id` 로 교체
- 필요 시 마이그레이션 기간 동안 아래 전략 사용
  - v1: `youtube_url` 또는 `video_id` 중 하나 허용
  - v2: `video_id` 만 허용

#### 2) YouTube caption fetch 함수 분리
현재 `_fetch_youtube_captions(youtube_url, start_sec, end_sec)` 는 함수 내부에서 URL parsing 을 수행합니다.

권장 변경:
- `fetch_youtube_captions(video_id, start_sec, end_sec)` 형태로 단순화
- URL parsing 책임 제거
- caption 조회 함수는 오직 `video_id` 만 알도록 축소

#### 3) yt-dlp 입력값 재구성
`yt-dlp` 는 보통 전체 URL 을 인자로 받는 편이 안정적이므로, Python 내부에서 아래처럼 **canonical URL 을 재조립**하는 것이 좋습니다.

- 내부 canonical URL: `https://www.youtube.com/watch?v={video_id}`
- 외부 입력은 `video_id` 만 받음
- 즉, 입력 계약은 단순화하고 외부 도구 호출 직전만 URL 생성

이 방식의 장점:
- API 계약은 작아짐
- `yt-dlp` 동작 안정성 유지
- 로그/추적에서 `video_id` 중심 관리 가능

#### 4) 응답 모델 변경
현재 `GenerateReferenceResponse` 는 `youtube_url` 을 반환합니다.

권장 방향:
- 우선순위 1: `video_id` 반환
- 필요 시 `youtube_url` 은 파생 필드로 함께 반환 가능

권장 예시:
```json
{
  "status": "SUCCESS",
  "video_id": "abc123xyz89",
  "youtube_url": "https://www.youtube.com/watch?v=abc123xyz89",
  "start_sec": 10.0,
  "end_sec": 25.0,
  "...": "..."
}
```

운영 관점에서는 `video_id` 를 기준 식별자로 삼고, `youtube_url` 은 표시용/디버깅용으로만 유지하는 편이 좋습니다.

#### 5) 로깅/메트릭 변경
- 로그 키를 `youtube_url` 중심에서 `video_id` 중심으로 변경
- 예외 로그, caption fallback 로그, yt-dlp 실패 로그에도 `video_id` 포함
- 운영 메트릭도 `video_id`, `stt_method`, `caption_status` 기준으로 집계

#### 6) 하위 호환 마이그레이션 전략
**1단계**
- 백엔드는 `video_id` 와 `youtube_url` 둘 다 전송
- Python 은 `video_id` 우선 사용, 없으면 `youtube_url` 에서 파싱

**2단계**
- Python 응답에 deprecation warning 또는 로그 추가
- `youtube_url` 입력 사용 비율 모니터링

**3단계**
- `youtube_url` 입력 제거
- 문서, API spec, 테스트 케이스 전면 갱신

---

### CHANGE-04. 백엔드/AI Worker 역할 분담 권장안

| 책임 | 백엔드 | Python AI Worker |
|------|--------|------------------|
| URL 유효성 검증 | ✅ | - |
| URL → video ID 정규화 | ✅ | - |
| video ID 저장/식별 | ✅ | - |
| caption 조회 | - | ✅ |
| yt-dlp 실행용 canonical URL 생성 | - | ✅ |
| 음성 분석/채점 | - | ✅ |

이 분담이 가장 자연스럽습니다.

---

## 5. 우선순위 제안

| 우선순위 | 항목 | 이유 |
|---------|------|------|
| 🔴 높음 | CUR-01 `s3://` 문서/구현 불일치 해소 | 실제 연동 장애 가능성 높음 |
| 🔴 높음 | CHANGE-03 video ID 계약 전환 준비 | 백엔드 계약 변경 예정 |
| 🟡 중간 | REF-01 `main.py` 역할 분리 | 앞으로의 변경 비용 절감 |
| 🟡 중간 | CUR-03 private helper 의존 제거 | 구조 안정성 향상 |
| 🟡 중간 | IMP-01 Pydantic 검증 강화 | 입력 품질 향상 |
| 🔵 낮음 | CUR-02 mutable default 정리 | 코드 품질/명시성 향상 |
| 🔵 낮음 | CUR-06 top-level pause 메타 추가 검토 | 확장성 개선 |

---

## 6. 결론

현재 `pipe/` 코드는 직전 리뷰에서 지적된 핵심 품질 문제를 상당수 해결한 안정적인 상태입니다.  
다음 단계의 핵심은 **알고리즘 수정**보다 **API 계약 정리와 구조 분리**입니다.

특히 이번 백엔드 변경 사항인 **YouTube 전체 URL → `video_id` 전환**은 단순한 필드명 변경이 아니라 다음 효과를 가집니다.

- API 계약 단순화
- 입력 포맷 책임의 백엔드 집중
- AI Worker 의 역할 명확화
- `main.py` / YouTube 연동부 리팩토링 촉진

따라서 다음 실제 구현 순서는 아래를 권장합니다.

1. API 계약서와 스키마 초안에서 `video_id` 필드 정의  
2. Python 내부에서 canonical URL 재조립 전략 확정  
3. caption fetch / yt-dlp 호출부를 service 함수로 분리  
4. 호환 기간 종료 후 `youtube_url` 입력 제거

**설계 담당자 제안**
추가 사항

- 사용자 전체 스코어 기반으로 통과, 불통과 여부 판단을 AI서버 내에서 수행하고 결과를 백엔드에 전달하는 방식으로 변경 (기준 점수 설정은 테스트 후 전체 점수 기준을 파악 후 최종 결정, 임시로 60점으로 설정)