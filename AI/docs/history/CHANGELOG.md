# Pipe Change Log

## 2026-03-26

### 평가 prosody 정렬 회귀 수정
- `AI/domain/scoring/aggregator.py`에서 유저 prosody 추출 호출 시 `hop_length`를 위치 인자가 아니라 `hop_length=` 키워드 인자로 전달하도록 수정했습니다. 이제 reference JSON에 저장된 hop length가 평가 단계에서도 그대로 적용됩니다.
- reference payload에 이미 `speech_start_sec:speech_end_sec` 기준으로 잘려 저장된 `f0_array` / `rms_array`를 평가 단계에서 다시 request-relative word timestamp로 재크롭하던 로직을 제거했습니다.
- 그 결과, 첫 단어 시작이 늦는 레퍼런스에서도 prosody, boundary tone, dynamic stress 점수가 잘못된 시간축으로 계산되던 회귀를 방지하고, non-default hop length reference와의 프레임 정렬도 복구했습니다.

### 평가 입력 분기 및 다운로드 안전성 강화
- `AI/services/evaluation_service.py`에서 S3 object key와 base64 입력을 명시적으로 분기하도록 수정했습니다. 더 이상 우연히 base64 디코딩이 성공하는 짧은 S3 key가 쓰레기 오디오 파일로 저장되지 않습니다.
- `AI/integrations/youtube_service.py`의 YouTube 다운로드 경로에 `--socket-timeout 15`, `--retries 3`, `timeout=60`을 적용해 yt-dlp 네트워크 지연과 장기 hang에 대한 이중 방어막을 추가했습니다.

### 평가 에러 코드 및 손상 오디오 방어 정리
- `AI/domain/scoring/evaluation_errors.py`를 추가해 평가 관련 공통 에러 코드와 메시지 규격을 중앙화했습니다.
- `AI/services/evaluation_service.py`에서 너무 짧은 오디오를 평가 진입 전에 `400`으로 거절하고, `AUDIO_INPUT_FORMAT_INVALID`, `AUDIO_TOO_SHORT`, `EVALUATION_INTERNAL_ERROR` 형태의 구조화된 `error_code`를 HTTP 응답에 담도록 정리했습니다.
- `AI/domain/scoring/aggregator.py`에서는 오디오 로드 실패 시 `AUDIO_LOAD_FAILED`, 무음/비음성 입력 시 `NO_VOICE_DETECTED`를 포함한 `FAIL` 응답을 반환하도록 통일했습니다.

### 요청별 trace 저장 추가
- `AI/services/request_trace_service.py`를 추가하고 `generate-reference`, `evaluate-audio` 요청마다 선별된 중간 feature와 최종 응답/에러를 `temp/request_traces` 하위 JSON 파일로 저장하도록 구현했습니다.
- trace에는 요청 요약, 입력 오디오 크기/유형, audio metrics, denoise mode, prosody frame 수, 품질/번역 메타데이터, 평가 점수 요약, 최종 상태 코드 등 데이터 축적과 후속 분석에 유의미한 피처만 선별해 저장합니다.

## 2026-03-25

### 유저 오디오 평가 VR(보컬 분리) 모드 지원
- 레퍼런스 생성 뿐 아니라, 유저가 업로드한 오디오 평가(`evaluate-audio`) 전에도 VR을 적용하여 배경 노이즈를 완전히 배제하고 순수한 목소리만 채점할 수 있는 옵션(`evaluation.user_vr_enabled`)을 추가했습니다.
- 백그라운드 소음이 있는 환경(예: 카페, 길거리)에서 발생하는 억울한 F0 인식 오류를 크게 줄였습니다.

### 억양 유사도(Prosody DTW) 정확도 확보 - Baseline 0 Fixed & Smart Cropping
- **Smart Cropping**: 유저의 STT 타임스탬프와 레퍼런스의 타임스탬프를 기준으로, 앞뒤 불필요한 무음 구간을 자르고 오직 "발화 구간"만 추려내어 정규화에 사용하도록 개선했습니다. 긴 무음이 Z-Score 평균을 왜곡하는 현상을 차단했습니다.
- **F0 & RMS 0점 고정(Baseline 0 Fixed)**: F0 추출 시 무성음(voiced_flag=False, 30Hz 이하) 구간과 RMS 볼륨 하위 15% 또는 극소 신호(1e-4 이하) 구간을 `0.0`으로 강제 고정했습니다.
- 결과적으로 무음 구간을 비교할 때 레퍼런스와 사용자 모두 `0`으로 시작하고 쉬게 되며, 이로 인해 DTW 알고리즘이 억지로 곡선을 정렬하려다 감점 폭탄을 내리던 오류를 완벽히 해결했습니다.

### 시각화 도구(Visualization) 프리미엄 고도화
- 시각적 디버깅 도구를 실제 서버 엔진과 100% 동일한 Baseline 0, Smart Cropping 로직으로 동기화했습니다.
- 시각화 자료를 Raw/Aligned, F0/RMS 별로 분리하여 **총 6종의 개별 프리미엄 해상도 PNG** 파일로 추출하도록 개편했습니다.

### LLM 번역 - Prosody 추출 완벽 병렬화 (Zero-Wait 전략)
- 기존에 순차적으로 진행되던 "Prosody 추출 → 병합 → LLM 번역" 직렬 구조를 타파하고 극한의 응답 시간을 위한 **비동기 포크(Fork)** 로직을 신설했습니다.
- 문장 뼈대가 분할되는 즉시(Step 5) `sentence_data`를 Deepcopy하여 Gemini 번역을 백그라운드로 Submit 합니다.
- **효과**: 네트워크 I/O 병목 시간(3~6초) 동안 서버가 쉬지 않고 가장 무거운 Prosody 추출 및 Source gating 연산을 동시 다발적으로 수행하여, 체감 응답 시간을 대폭 40~50% 감소시켰습니다.


### 코드 컨벤션 전면 정비 (Google Style Docstring)
- `AI/` 디렉터리 내 **모든 Python 파일**의 docstring 을 Google Style 로 통일했습니다.
  - 모듈 docstring: `"""제목.\n\n설명."""` 형식으로 정규화.
  - 함수/메서드 docstring: `Args`, `Returns`, `Raises`, `Yields` 섹션 추가.
  - NumPy Style (`Parameters` / `Returns` + `-------`) → Google Style 전환.
- `pipeline.py` 의 `StyleEchoPipeline` 클래스 전체 메서드(15개+)에 Google Style docstring 적용.
- `domain/processing/` 전 모듈(`constants.py`, `engine_utils.py`, `quality.py`, `speaker_analysis.py`, `text_processing.py`, `audio_processing.py`)에 `Args`/`Returns` 섹션 추가.
- `api/` 라우터(`system.py`, `app.py`, `evaluation.py`, `reference.py`)에 타입 힌트 보강 및 docstring 통일.
- `integrations/youtube_service.py` 의 `download_reference_audio`, `fetch_youtube_captions` 에 `Args`/`Returns`/`Raises` 추가.
- `config.py` 의 내부 함수(`_ensure_dict_config`, `_load_config`, `get`) docstring 보강.
- `integrations/audio_cache.py` 의 `typing.Optional` → `| None` 현대 문법 전환 및 `from __future__ import annotations` 적용.

### 기획발표용 상세 아키텍처 및 워크플로우 문서 추가
- `AI/docs/presentation/` 경로에 기획 발표용 상세 명세 문서 3종 신설.
  - `01_service_overview.md`: 전체 기능 개요, 기술 스택, 6대 AI 특화 기능.
  - `02_reference_generation_workflow.md`: YouTube 레퍼런스 생성 파이프라인 12단계.
  - `03_evaluation_scoring_workflow.md`: 유저 오디오 7대 채점 지표 산출 10단계 및 가중치 수식.

### VR Source Mode(오디오 소스 라우팅) 설정 추가
- 유튜브 영상 처리 시 오리지널 음원과 VR(보컬 분리) 음원의 활용 방식을 설정 다이얼로 선택하도록 분기 구현.
- `config_default.yaml` 및 `config.py`에 `vocal_remover.source_mode`(`VR_SOURCE_MODE`) 추가.
  - `original`: 가장 빠름 (VR 연산 자체 생략, 보컬 분리 없이 추출됨).
  - `vr`: 깨끗한 목소리 우선 (원본 피처 추출 생략).
  - `both`: 두 음원의 프로소디 추출 이후 품질 게이팅(`select_reference_prosody_sources`) 적용.

### ONNX 기반 VR 전용 테스트 도구 및 인프라 개선
- ONNX 추론 지원을 위해 `setup.sh`에 `onnxruntime-gpu` (CUDA 12 지원) 부분 추가.
- 유튜브 URL 또는 ID와 시간(start/end_sec)을 입력받아 원본과 VR 분리 버전을 추출·비교할 수 있는 `test/test_vr_onnx.py` 신설.
- `.gitignore` 갱신을 통해 테스트 결과 및 아티팩트 무시 처리 적용.

### 오디오 아티팩트 저장 로직 최적화 및 디버그 로깅 정리
- `test/result/`에 무분별하게 쌓이던 테스트 데이터 및 파트별 오디오 생성을 `config.SAVE_REFERENCE_AUDIO` (`reference.save_audio_artifacts`) 플래그로 제어하도록 변경하여 디스크 I/O 최적화.
- 불필요한 `print` 문(`audio_processing.py`)을 `logger.debug`로 정비하여 로그 출력 정제.
- `select_reference_prosody_sources` 메서드 호출 부분의 `AttributeError` 버그 해결 (모듈 직접 참조로 수정).
 
## 2026-03-23

### 스코어링 하이퍼파라미터 설정화
- `pipeline.py` 내 하드코딩된 점수 산출 상수들을 `config_default.yaml` / `config.py` 에서 튜닝 가능하도록 추출했습니다.
  - `wer_penalty`, `boundary_k`, `boundary_good_threshold`, `dynamic_k`, `dynamic_good_threshold`, `pitch_flat_threshold_hz` 등 추가
- `test/scoring_verify.py` 도 config 참조로 통일했습니다.

### 종결 억양(Boundary Tone) 알고리즘 개선
- **F0 이동평균 평활화(window=3)** 적용으로 노이즈 왜곡 방지.
- **절대 시간 하한(300ms)** 도입: 짧은 발화에서 마지막 15% 구간이 너무 짧아지는 문제 해결.
- **Soft Ratio bias(0.3)** 도입: `(min + bias) / (max + bias)` 형태로 평음 근처에서 점수 폭락(1~2점) 방지.
- **Opposite Flat Zone 예외**: 부호가 반대여도 양쪽 모두 평음에 가깝다면 80점(기존 40점)으로 완화.
- `boundary_k` 기본값을 `0.8` → `0.5`로 조정하여 기울기 크기 차이에 대해 관대하게 변경.

### 속도 유사도(Speed) 불감대(Deadband) 도입
- `|ratio - 1.0| ≤ 0.1` (±10%) 구간은 100점 처리.
- 불감대 경계 밖에서부터 기존 거듭제곱 감점을 적용하여 미세한 속도 차이로 인한 불필요한 감점 제거.

### 피치 컨투어 임계값 단위 통일 (Hz → 비율)
- 단어별 피치 변화 판정을 절대값(5Hz)에서 **평균 F0 대비 비율(4%)** 기반으로 변경.
- 남성/여성/아동 등 화자의 기본 피치 대역에 무관하게 일관된 flat 판정 제공.

### 문서 업데이트
- `docs/internals/04_scoring_logic.md` 를 변경된 수식 및 config 참조에 맞게 전면 갱신.

## 2026-03-16

- 전역 설정 로더를 `OmegaConf` 기반의 딥 머지(Deep Merge) 방식으로 전환하여 `config.yaml` 의 부분 오버라이드가 완벽히 동작하도록 개선했습니다.
- `boto3` 등 필수 의존성 import 를 모듈 최상단으로 올리고, 무거운 선택적 의존성(`audio_separator`, `torch` 등)만 함수 내 지연 로딩을 유지하도록 import 정책을 일관되게 정리했습니다.
- `services/` 및 `integrations/` 전반의 핵심 함수들에 대해 Google Style Docstring 을 엄격하게 적용하여 문서화 일관성을 높였습니다.
- 불필요한 `importlib` 및 어색한 모듈 alias 참조 패턴을 제거하고 정적 분석을 돕기 위해 반환 타입 힌트를 `dict[str, Any]` 와 같이 구체화했습니다.
- **AWS S3 연동:** `evaluate-audio` 요청 시 `user_audio`로 S3 객체 URL(`s3://...` 또는 HTTPS S3 URL)을 지원하도록 개선했습니다.
  - `boto3` 라이브러리를 의존성에 추가했습니다.
  - S3 버킷, 지역, 액세스 키 등의 설정을 `config.py` 및 `config_default.yaml`에 추가하고 `.env`를 통한 주입이 가능하도록 구현했습니다.
- **Import 시스템 리팩토링:** 프로젝트 전반의 `try...except ImportError` 블록을 제거하고, `AI/` 루트를 기준으로 한 **절대 경로(Absolute Import)** 방식으로 참조 체계를 통일했습니다. 
  - 이로 인해 실행 환경에 따른 import 오류 가능성을 줄이고 코드 가독성을 향상시켰습니다.
- `AI/README.md` 및 `docs/internals/03_evaluation_process.md` 문서를 최신 변경 사항에 맞게 갱신했습니다.

## 2026-03-13

- `generate-reference` 내부 처리 순서를 재구성해 자막 조회 ↔ 오디오 다운로드, VR ↔ STT/alignment, 원본 prosody ↔ VR prosody, Gemini 번역 ↔ prosody/quality assessment 구간을 병렬화했습니다.
- Gemini 번역 요청을 `sentence_data` 생성 직후 시작하도록 조정하고, 병렬 구간에서 경쟁 상태를 피하기 위해 `deepcopy(sentence_data)`를 사용하도록 정리했습니다.
- reference 생성에서 STT / forced alignment 및 품질 평가는 원본 오디오 기준으로 유지하고, prosody는 원본 / VR 후보를 gating 규칙으로 선택하도록 역할을 분리했습니다.
- `vocal_remover.enabled` 설정으로 reference 생성에서 VR 경로 전체를 제어할 수 있게 했고, `false`일 때는 vocal separation, VR prosody 추출, source gating 비교를 모두 스킵하도록 반영했습니다.
- `AI/README.md`, `docs/README.md`, `docs/internals/02_reference_generation.md`를 현재 동작에 맞게 갱신했습니다.

## 2026-03-12

- `pipeline.py` 내의 중복 F0 정규화 및 빈 결과 dict 생성 로직을 헬퍼 함수로 통합했습니다.
- `pipeline.py`의 단어 매칭 검색 알고리즘을 선형 스캔($O(n^2)$)에서 `dict` 기반 룩업($O(n)$)으로 최적화했습니다.
- `reference_service.py`의 리소스 정리 로직을 `finally` 블록으로 통합하여 안정성을 높이고 코드 중복을 제거했습니다.
- `quality.py`와 `reference_service.py` 간의 오디오 메트릭 계산 중복 호출을 제거하고 결과를 재사용하도록 개선했습니다.
- `A1_WORDS` 상수 내 중복 항목 제거 및 번역 서비스 내 화자 분석 로직 임포트 재사용 등 전반적인 코드 클린업을 수행했습니다.
- 상세 변경 내역은 `docs/review/code_refactoring_optimization_2026-03-12.md`에 기록했습니다.

## 2026-03-11

- `generate-reference`의 내부 패딩은 유지하되 최종 `reference.json`, `full_audio.wav`, part 오디오의 시간 기준을 요청 구간 기준(A)으로 통일했습니다.
- manual caption 경로에서 padded clip 기준 timestamp 가 외부 응답으로 섞이던 문제를 줄이기 위해 word timestamp rebasing 로직을 추가했습니다.
- evaluate 경로의 `_align_user_words_to_ref()`를 연음/축약 canonical token sequence 기반으로 재구성했습니다.
- `gonna`, `wanna`, `gotta`, `lemme`, `gimme`, `I'm` 류 표현이 evaluate 정렬에서 더 자연스럽게 매칭되도록 `engine_utils` canonicalization 유틸을 추가했습니다.
- 관련 변경 배경과 검증 포인트를 `docs/review/timebase_and_alignment_2026-03-11.md`에 기록했습니다.
- 공개 `generate-reference` 응답에서 내부 수치처리/디버그 메타데이터를 제외하고, 백엔드 전달에 필요한 핵심 필드만 남기도록 응답 계약을 축소했습니다.
- `parts.word_timestamps`에서는 `speaker`, `score`를 제거했고, top-level 번역 상태는 `translation_status` 대신 `translation_success`로 정리했습니다.

## 2026-03-10

- active 문서와 archive 문서를 분리하는 기준을 정리했습니다.
- `docs/legacy/`를 archive 전용 폴더로 확정했습니다.
- 기록성 문서를 `docs/` 하위로 이동해 정리했습니다.
- `main.py` 추가 분리를 위한 책임 정의 문서를 작성했습니다.
- FastAPI 앱 구성을 `api/`, `services/`, `integrations/` 패키지로 분리했습니다.
- reference payload helper를 `services/reference_payload.py`로 이동했습니다.
- 핵심 처리 로직을 `domain/processing` 패키지로 이동하기 시작했습니다.
- 루트 `main.py`는 최소 FastAPI 진입점(`pipe.main:app`)으로 유지했습니다.
- 루트 처리 모듈(`audio_processing.py`, `quality.py`, `text_processing.py` 등)은 제거하고 `domain/processing` 경로를 기준으로 정리했습니다.
- `HTTPException` 경로에서 임시 파일 정리가 누락될 수 있는 부분을 서비스 레이어에서 보완했습니다.

## 2026-03-09

- `generate-reference` 입력 계약을 `youtube_url`에서 `video_id`로 전환했습니다.
- `evaluate-audio` 응답에 `pass_fail`, `pass_threshold`를 추가했습니다.
- `main.py` 책임 일부를 `schemas.py`, `youtube_service.py`, `io_utils.py`, `reference_service.py`로 분리했습니다.
- `generate-reference` top-level 응답에 `pause_count`, `active_speech_sec`, `word_count`를 추가했습니다.
- short-part merge의 terminal punctuation 보호를 제어하는 설정을 추가했습니다.
- `engine.py`를 하위 호환 re-export hub로 정리했습니다.

## 2026-03-05 ~ 2026-03-06

- 채점 수식, pause 계산, speed penalty, boundary tone, dynamic stress 기준을 정리했습니다.
- JSON body 기반 `evaluate-audio` 요청 구조를 정비했습니다.
- 번역, pitch contour feedback, reduction detection, difficulty 분류 관련 개선을 반영했습니다.
