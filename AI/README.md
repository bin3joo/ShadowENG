# StyleEcho AI Worker Specification

## 1. 개요

* **목표:** YouTube 구간을 기반으로 영어 말하기 레퍼런스를 자동 생성하고, 유저 발화 오디오를 레퍼런스와 비교하여 발음/억양/리듬을 평가하는 AI Worker 서버 구축.
* **주요 역할:**
  * YouTube `video_id` + 시간 구간 입력 기반 reference 생성
  * 문장/턴 단위 part 분할 및 prosody feature 추출
  * 유저 오디오 평가 및 종합 점수 산출
  * reference 품질 판정, 화자 분석, dialog turn 메타데이터 제공
* **비고:** FastAPI 기반 Python 서버이며, 메인 서버 또는 테스트 스크립트가 HTTP API로 호출하는 Worker 역할을 수행합니다.

## 2. 개발 환경 및 공통 설정

* **언어:** Python 3.10 권장
* **서버 프레임워크:** FastAPI
* **환경 관리:** `requirements.txt` 기반 Python 환경
* **오디오 처리:** `librosa`, `numpy`, `scipy`
* **모델 실행:** WhisperX / pyannote.audio / Transformers
* **GPU:** CUDA 사용 가능 시 GPU 사용 권장

## 3. 사용할 라이브러리 (Tech Stack)

* **Web Framework:** `fastapi`, `uvicorn`, `pydantic`
* **STT / Alignment:** `whisperx`, `faster-whisper`
* **Speaker Diarization:** `pyannote.audio`
* **Audio / Signal:** `librosa`, `numpy`, `scipy`, `noisereduce`
* **Scoring / Text Eval:** `fastdtw`, `jiwer`
* **Translation:** `transformers`
* **YouTube / Config:** `yt-dlp`, `youtube-transcript-api`, `PyYAML`

## 4. 기능 상세 명세

### 4.1. 인터페이스 (API)

* **프로토콜:** HTTP POST
* **요청 포맷 (Request):** JSON
* **응답 포맷 (Response):** JSON
* **Base Path:** `/api/v1`
* **Endpoints:**
  * `POST /api/v1/generate-reference`
  * `POST /api/v1/evaluate-audio`

### 4.2. 레퍼런스 생성 (`generate-reference`)

* **역할:** YouTube `video_id` 와 시간 구간을 입력받아 reference 스크립트, part 정보, 단어 타임스탬프, F0/RMS feature를 생성합니다.
* **입력:** `video_id`, `start_sec`, `end_sec`
* **출력:**
  * `final_script`
  * `parts[]`
  * `word_timestamps`
  * `pause_count`, `active_speech_sec`, `word_count`
  * `reference_quality`, `warnings`, `denoise_mode`
  * `speaker_mode`, `dialog_mode`, `turn_count`
* **특징:**
  * 자막 기반 `caption_align` fast path 우선 시도
  * 실패 시 Whisper STT fallback
  * boundary trim 적용
  * part 단위 prosody feature 추출
  * quality / overlap / speaker risk 분석 포함

### 4.3. 유저 발화 평가 (`evaluate-audio`)

* **역할:** 유저 오디오를 레퍼런스와 비교하여 발음, 억양, 리듬, 속도, pause 유사도를 평가합니다.
* **입력:**
  * `user_audio` (HTTP URL 또는 Base64)
  * `user_audio_format`
  * `final_script`
  * `features`
  * `word_timestamps`
* **출력:**
  * 단어 정확도 관련 지표
  * prosody / rhythm / boundary tone / dynamic stress 점수
  * 속도 유사도, pause 유사도
  * 종합 점수 및 pass/fail 판단

### 4.4. 화자 분석 및 dialog turn 분할

* **역할:** reference 오디오의 화자 수와 화자 전환 패턴을 분석하고, pause 및 punctuation 기반으로 dialog turn을 분할합니다.
* **포함 메타데이터:**
  * `speaker_risk`
  * `dominant_speaker`
  * `speaker_count`
  * `speaker_mode`
  * `dialog_mode`
  * `turn_id`
  * `turn_break_reason`

### 4.5. reference 품질 평가

* **역할:** 생성된 reference가 학습/따라말하기에 적합한지 판단합니다.
* **평가 요소:**
  * estimated SNR
  * alignment median score
  * low alignment ratio
  * overlap risk ratio
  * speech ratio
  * multi-speaker risk
* **정책:**
  * `good`, `risky`, `reject` 레벨로 분류
  * 설정에 따라 risky 허용 / 특정 조건 reject 가능

### 4.6. 번역 기능

* **역할:** 영어 스크립트 및 문장 part를 한국어로 번역합니다.
* **모델:** `Helsinki-NLP/opus-mt-en-ko`
* **비고:** 번역 모델 로드 실패 시 `None` 을 반환하며, 핵심 파이프라인은 계속 동작합니다.

## 5. 데이터 흐름 (Pipeline)

1. **Request:** 클라이언트 또는 메인 서버가 `/api/v1/generate-reference` 또는 `/api/v1/evaluate-audio` 로 요청을 전송합니다.

2. **Process:**
* `[Generate Reference]`
  * YouTube 자막 조회
  * 구간 오디오 다운로드
  * caption align 또는 Whisper STT 수행
  * boundary trim 및 텍스트 정제
  * prosody feature 추출
  * 문장/turn 분할 및 short-part merge
  * speaker / quality 분석
  * 응답 payload 생성
* `[Evaluate Audio]`
  * 유저 오디오 로드
  * reference feature / word timestamp 와 정렬
  * prosody / rhythm / speed / pause 비교
  * 종합 점수 계산 및 결과 반환

3. **Response:** JSON 형태로 결과를 반환합니다.

---

## 5.1. 실행 방법 (개발)

* **패키지 설치:**
  * `pip install -r requirements.txt`
* **서버 실행 (예시):**
  * 프로젝트 루트 경로에서 실행
  * `uvicorn main:app --reload --host 0.0.0.0 --port 8000`

## 6. Quick Start (Usage)

### 6.1. 사전 요구사항

* **Git** — 리포지터리 클론용
* **Miniforge3** (또는 Miniconda / Anaconda) — conda 환경 관리
* **CUDA Toolkit** (선택) — GPU 가속 사용 시 필요 (CUDA 11.8 이상 권장)
* **ffmpeg** — `yt-dlp` 오디오 변환에 필요

### 6.2. 리포지터리 클론

```bash
git clone <REPOSITORY_URL>
cd S14P21A306/AI
```

### 6.3. Miniforge3 환경 생성 및 패키지 설치

```bash
# 1. conda 환경 생성 (Python 3.10)
conda create -n styleecho python=3.10 -y
conda activate styleecho

# 2. (GPU 사용 시) PyTorch CUDA 버전 설치
#    CUDA 11.8 기준 예시 — 본인 CUDA 버전에 맞게 조정
conda install pytorch==2.0.1 torchaudio==2.0.2 torchvision==0.15.2 pytorch-cuda=11.8 -c pytorch -c nvidia -y

# 3. 나머지 의존성 설치
pip install -r requirements.txt
```

> **CPU 전용 환경**인 경우 2번 단계를 건너뛰면 `requirements.txt`의 PyTorch CPU 빌드가 설치됩니다.
> `config.yaml`에서 `whisper.device: "cpu"`, `whisper.compute_type: "int8"` 로 변경하세요.

### 6.4. 환경 변수 설정 (`.env`)

프로젝트 루트(`AI/`)에 `.env` 파일을 생성합니다. (`.gitignore`에 포함되어 있어 커밋되지 않습니다.)

```bash
# AI/.env
GMS_API_KEY=<Gemini API Key>
HF_TOKEN=<Hugging Face Token>
```

| 변수 | 용도 | 필수 여부 |
|---|---|---|
| `GMS_API_KEY` | Gemini 번역/병합/학습 표현 추출 | 선택 (없으면 번역 비활성) |
| `HF_TOKEN` | pyannote speaker-diarization 모델 접근 | 선택 (없으면 diarization 비활성) |

> 두 키가 모두 없어도 레퍼런스 생성과 평가의 핵심 기능은 정상 동작합니다.

### 6.5. 설정 커스터마이징

`config.yaml` 을 수정하여 기본 설정을 override 할 수 있습니다.
변경이 필요한 항목만 넣으면 나머지는 `config_default.yaml` 기본값이 적용됩니다.

```yaml
# config.yaml 예시 (GPU 환경)
whisper:
  model: "large-v3"
  device: "cuda"
  compute_type: "float16"
```

```yaml
# config.yaml 예시 (CPU 환경)
whisper:
  model: "base"
  device: "cpu"
  compute_type: "int8"
```

### 6.6. 서버 실행

```bash
# AI/ 디렉터리에서 실행
uvicorn main:app --host 0.0.0.0 --port 8000
```

개발 모드 (자동 리로드):

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

서버 시작 시 WhisperX 모델이 preload 됩니다. 첫 실행 시 모델 다운로드로 시간이 걸릴 수 있습니다.

### 6.6.1. 프로토타입 운영 메모

- 현재 구성은 **실서비스 배포용이 아니라 검증용 프로토타입**을 전제로 합니다.
- 메인 백엔드는 AWS에서 실행하고, AI 서버는 **GPU가 있는 개인 노트북**에서 실행할 수 있습니다.
- 이 경우 AI 서버는 직접 공인 포트로 노출하기보다 **로컬 바인딩 + 터널링 도구** 사용을 권장합니다.
- 권장 흐름은 다음과 같습니다.
  - AI 서버: `uvicorn main:app --host 127.0.0.1 --port 8000`
  - 외부 연결: `ngrok` 또는 유사 터널을 통해 임시 공개 URL 발급
  - 메인 백엔드: 해당 터널 URL로만 AI API 호출
- 이 구성은 어디까지나 시연/검증 단계용이며, 실제 운영 단계에서는 **AWS 내부 네트워크 또는 별도 GPU 서버**로 이전하는 것을 권장합니다.

### 6.7. API 테스트

서버가 실행 중인 상태에서 별도 터미널을 열어 실행합니다.

```bash
cd S14P21A306/AI

# 레퍼런스 생성 테스트 (YouTube video_id 또는 URL)
python -m test.test_api generate "VIDEO_ID" 30.0 45.0
python -m test.test_api generate "https://www.youtube.com/watch?v=VIDEO_ID" 30.0 45.0

# 유저 발화 평가 테스트 (가장 최근 생성된 레퍼런스 기준)
python -m test.test_api evaluate "./my_recording.wav"

# 특정 레퍼런스 + 특정 파트 평가
python -m test.test_api evaluate "./my_recording.wav" --ref "./test/result/VIDEO_ID/meta/reference.json" --part 2
```

### 6.8. 검증 스크립트

```bash
cd S14P21A306/AI

python -m test.sentence_verify    # 문장 분리 검증
python -m test.trim_verify        # trim 로직 검증
python -m test.fix_verify         # 보정 로직 검증
```

검증 결과는 `test/result/` 아래에 텍스트 파일로 저장됩니다.

### 6.9. 테스트 결과 경로

| 항목 | 경로 |
|---|---|
| 레퍼런스 메타 JSON | `test/result/{video_id}/meta/reference.json` |
| 평가 결과 JSON | `test/result/{video_id}/meta/evaluate_result.json` |
| 전체 오디오 | `test/result/{video_id}/ref_audio/full_audio.wav` |
| 파트별 오디오 | `test/result/{video_id}/ref_audio/parts/part_01.wav` |
| 검증 스크립트 출력 | `test/result/*.txt` |

---

## 7. 포팅/배포 참고 (Porting Guide)

### 7.1. 실행 환경

* **Python:** 3.10 권장
* **Framework:** FastAPI (`uvicorn`)
* **GPU:** CUDA 사용 시 `config.yaml` 의 `whisper.device`, `whisper.compute_type` 설정 확인
* **메모리:** WhisperX / pyannote.audio / translation model 사용량을 고려해야 합니다.

### 7.2. 설정 파일

* **`config_default.yaml`**
  * 전체 기본 설정 정의 파일
  * Whisper, server, audio, denoise, reference, trimming, scoring, alignment 설정 포함

* **`config.yaml`**
  * 사용자 커스텀 설정 파일
  * 변경할 값만 override 가능
  * 주요 설정 예시:
    * `whisper.model`, `whisper.device`, `whisper.compute_type`
    * `reference.enable_diarization`
    * `reference.denoise_mode`
    * `reference.min_part_duration_sec`
    * `reference.short_part_terminal_protection_enabled`
    * `alignment.caption_fallback_enabled`

### 7.3. 외부 서비스 / 토큰

* **YouTube 자막 및 오디오 접근**
  * `youtube-transcript-api`, `yt-dlp` 사용
  * 네트워크 접근이 필요합니다.

* **Gemini API Key (`GMS_API_KEY`)**
  * 레퍼런스 전체 번역, 파트 병합 번역, 학습 표현 추출에 사용됩니다.
  * 프로젝트 루트 `.env`에 `GMS_API_KEY=...` 형태로 저장해 사용합니다.
  * 키가 없거나 Gemini 호출이 반복 실패하면 번역 관련 필드만 비어 있고 레퍼런스 생성 자체는 계속 진행됩니다.
  * 번역 실패 시 설정된 재시도 횟수 내에서 자동 재시도합니다.

* **Hugging Face Token (`HF_TOKEN` 또는 `HUGGINGFACE_TOKEN`)**
  * `pyannote/speaker-diarization-3.1` 사용 시 필요할 수 있습니다.
  * 토큰이 없으면 diarization은 비활성화되고 나머지 파이프라인은 계속 동작합니다.

### 7.4. 배포/운영 시 특이사항

* **WhisperX 모델 preload**
  * FastAPI lifespan 시점에 pipeline preload 수행
  * 첫 요청 지연을 줄이지만 서버 시작 시간이 길어질 수 있습니다.

* **임시 오디오 파일 관리**
  * reference 생성 과정에서 임시 디렉토리와 오디오 파일이 생성됩니다.
  * 처리 완료 후 `BackgroundTasks` 로 정리합니다.

* **Gemini 번역/병합 재시도**
  * 레퍼런스 생성 시 Gemini를 사용해 전체 번역, 파트 병합, 파트별 번역, 학습 표현 추출을 수행합니다.
  * Gemini 응답 파싱 또는 번역 실패 시 설정된 횟수만큼 재시도합니다.
  * 최종 실패 시에도 레퍼런스 생성은 유지되고, 번역 관련 필드만 비어 있을 수 있습니다.

* **Diarization 정책**
  * 토큰/권한 부족 시 diarization만 비활성화되며 API 전체 실패로 이어지지 않도록 설계되어 있습니다.

### 7.5. 테스트 실행

* **API 테스트**
  * `python -m test.test_api generate VIDEO_ID 30.0 45.0`
  * `python -m test.test_api evaluate "./my_recording.wav"`

* **검증 스크립트**
  * `python -m test.sentence_verify`
  * `python -m test.scoring_verify`
  * `python -m test.trim_verify`
  * `python -m test.fix_verify`

* **결과 저장 경로**
  * 테스트 산출물은 `test/result/` 아래에 저장됩니다.
  * 레퍼런스 테스트 산출물은 `test/result/{video_id}/` 아래에 저장됩니다.
  * 메타데이터 JSON은 `test/result/{video_id}/meta/reference.json`에 저장됩니다.
  * 평가 결과 JSON은 `test/result/{video_id}/meta/evaluate_result.json`에 저장됩니다.
  * 레퍼런스 오디오는 `test/result/{video_id}/ref_audio/`에 저장됩니다.
  * `ref_audio/` 상단에는 `full_audio.wav`가 저장되고, `ref_audio/parts/` 아래에 파트별 오디오가 저장됩니다.

### 7.6. 문서 운영 정책

* **Active 문서**
  * `README.md`: 현재 시스템 개요, 실행 방법, 설정, 구조
  * `docs/review/`: 코드 리뷰 및 현재 개선 과제 문서 관리 위치
  * `docs/history/CHANGELOG.md`: 날짜 기반 변경 이력 문서
  * `docs/architecture/main_refactor_plan.md`: `main.py` 추가 분리 기준 문서

* **Legacy 문서**
  * 과거 분석, 제안, API 기록, 이미 반영된 변경 이력은 `docs/legacy/` 폴더에 보관합니다.

* **구조 운영 상태**
  * 코드 리뷰 문서는 `docs/review/`로 이동되었습니다.
  * 튜닝 문서는 `docs/tuning/`으로 이동되었습니다.
  * 루트 `main.py`는 최소 FastAPI 진입점으로 유지합니다.
  * 앱 구성은 `api/`, `services/`, `integrations/`로 분리하고, 핵심 처리 로직은 `domain/processing`으로 이동했습니다.

---

# 폴더 구조 (Directory Structure)

```plaintext
AI/
├── __init__.py
├── main.py                        # 최소 FastAPI 진입점 (`main:app`)
├── schemas.py                     # 요청/응답 Pydantic 스키마
├── config.py                      # config_default.yaml + config.yaml 로딩
├── config_default.yaml            # 기본 설정 스펙
├── config.yaml                    # 사용자 커스텀 설정
├── requirements.txt               # Python 의존성 목록
├── README.md                      # 현재 시스템 설명, 실행 방법, 설정, 구조
├── pipeline.py                    # StyleEchoPipeline 핵심 로직
├── domain/
│   ├── __init__.py
│   └── processing/
│       ├── __init__.py
│       ├── audio_processing.py    # 실제 오디오 처리 구현
│       ├── constants.py           # 실제 상수 정의
│       ├── engine_utils.py        # 실제 공통 유틸리티 구현
│       ├── quality.py             # 실제 품질 평가 구현
│       ├── speaker_analysis.py    # 실제 화자 분석 구현
│       └── text_processing.py     # 실제 문장/turn 처리 구현
├── api/
│   ├── __init__.py
│   ├── app.py                     # FastAPI app factory 및 router 조립
│   ├── evaluation.py              # evaluate-audio endpoint
│   └── reference.py               # generate-reference endpoint
├── services/
│   ├── __init__.py
│   ├── evaluation_service.py      # 유저 오디오 평가 orchestration
│   ├── reference_payload.py       # reference payload/helper 조립
│   └── reference_service.py       # reference 생성 orchestration
├── integrations/
│   ├── __init__.py
│   ├── io_utils.py                # 파일/오디오 I/O 실제 구현
│   └── youtube_service.py         # YouTube 연동 실제 구현
├── test/
│   ├── __init__.py
│   ├── test_api.py                # API 수동 테스트 CLI
│   ├── sentence_verify.py         # 문장 분리 검증 스크립트
│   ├── scoring_verify.py          # 채점 로직 검증 스크립트
│   ├── trim_verify.py             # trim 로직 검증 스크립트
│   ├── fix_verify.py              # 보정 로직 검증 스크립트
│   ├── test_utils.py              # 테스트 출력 저장 유틸리티
│   └── result/
│       └── .gitkeep               # 테스트 결과 저장 디렉터리
├── docs/
│   ├── README.md                  # active 기록성 문서 운영 정책
│   ├── review/
│   │   ├── README.md              # 코드 리뷰 문서 배치 기준
│   │   ├── ...                    # 날짜 기반 코드 리뷰 문서
│   ├── tuning/
│   │   ├── README.md              # 튜닝 문서 배치 기준
│   │   └── ...                    # 날짜 기반 튜닝 문서
│   ├── history/
│   │   ├── README.md              # 변경 이력 문서 배치 기준
│   │   └── CHANGELOG.md           # 날짜 기반 변경 이력
│   ├── legacy/
│   │   ├── README.md              # 레거시 문서 보관 정책
│   │   ├── ...                    # 날짜 기반 레거시 문서
│   └── architecture/
│       └── ...                    # 날짜 기반 아키텍처 문서
```
