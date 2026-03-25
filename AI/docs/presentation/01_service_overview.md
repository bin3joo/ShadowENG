# 잉무 AI 서비스 — 전체 기능 개요

## 1. 서비스 목적

잉무 AI는 **영어 발화 학습** 플랫폼을 위한 백엔드 AI 서버입니다.  
사용자가 YouTube 영상 속 원어민 발화를 **따라 읽고**, AI가 **7가지 지표**로 정밀 평가·피드백을 제공합니다.

---

## 2. 핵심 API

| API | 역할 | 주요 응답 |
|---|---|---|
| `POST /api/v1/generate-reference` | YouTube 영상의 특정 구간을 분석하여 학습 레퍼런스를 생성 | 파트별 문장·타임스탬프·F0/RMS 피처·한국어 번역·어휘·학습표현 |
| `POST /api/v1/evaluate-audio` | 사용자 녹음을 레퍼런스와 비교하여 발화 점수 평가 | 7대 점수·PASS/FAIL·단어별 피드백·피치 컨투어 |

---

## 3. 기술 스택

### 3.1 AI / ML 모델

| 기술 | 용도 |
|---|---|
| **WhisperX** (large-v3) | 음성 인식(STT) + Forced Alignment + 단어 타임스탬프 |
| **pyannote.audio** | 화자 분리(Diarization) — 다중 화자 감지 |
| **audio-separator** (htdemucs_ft / UVR-MDX-NET ONNX) | 보컬 분리(Vocal Remover) — 배경 음악/노이즈 제거 |
| **librosa** (pyin + RMS) | F0(피치)·RMS(에너지) 프로소디 피처 추출 |
| **Gemini LLM** (gemini-2.5-flash) | 한국어 번역, 파트 병합, 어휘/학습표현 추출 |

### 3.2 서버 프레임워크

| 기술 | 용도 |
|---|---|
| **FastAPI** | 비동기 REST API 서버 |
| **Pydantic** | 요청/응답 스키마 검증 |
| **yt-dlp** | YouTube 오디오 다운로드 |
| **youtube-transcript-api** | YouTube 자막 조회 |
| **noisereduce** | Track B 디노이징 (사용자 오디오 분석용) |
| **boto3** | S3 오디오 업로드/다운로드 |

---

## 4. AI 특화 기능 요약

### 4.1 Prosody Feature Extraction (프로소디 피처 추출)
- **F0 (피치)**: `librosa.pyin`으로 C2~C7 주파수 대역에서 프레임별 기본 주파수를 추출
  - **Baseline 0 Fixed**: `voiced_flag` 기반 무성음/30Hz 미만 구간을 `0.0`으로 마스킹 후 메디안 필터(kernel=5) 적용
  - 유성음 구간만으로 Z-score 정규화
- **RMS (에너지)**: `librosa.feature.rms`로 프레임별 에너지를 추출
  - **Baseline 0 Fixed**: 하위 15% 이하 또는 극소 신호(1e-4) 구간을 `0.0`으로 강제 고정 후 Z-score 정규화
- **Smart Cropping**: STT 타임스탬프 기반으로 실제 발화 구간만 잘라내어 앞뒤 무음의 평균 왜곡 차단
- **Hop Length**: 256 프레임 (16kHz 기준 ~16ms 해상도)

### 4.2 VR Gating (보컬 분리 게이팅)
원본 오디오와 VR(보컬 분리) 오디오의 F0/RMS를 **4가지 품질 지표**로 비교하여, 더 나은 소스를 자동 선택합니다:
- **Voiced Ratio** (F0 유성음 비율) — 클수록 좋음
- **Jump Ratio** (F0 옥타브 도약 비율) — 작을수록 좋음
- **Contrast dB** (RMS 음량 대조비) — 클수록 좋음
- **Dropout Ratio** (RMS 신호 소실 비율) — 작을수록 좋음

### 4.3 Speaker Analysis (화자 분석)
- pyannote.audio diarization으로 화자별 라벨링
- 파트별 dominant speaker 비율, speaker label change ratio 등으로 다중 화자 감지
- F0 median 기반 인접 파트 간 화자 전환(semitone shift) 추정

### 4.4 Quality Gate (품질 게이트)
레퍼런스 오디오의 적합성을 **good / risky / reject** 3단계로 판정:
- SNR(신호 대 잡음비) 및 speech ratio 기반 노이즈 레벨 추정
- 정렬 신뢰도(median alignment score, low-confidence 단어 비율)
- 타임스탬프 겹침 비율(overlap risk)
- 화자 일관성 정책

### 4.5 Adaptive Denoising (적응형 디노이징)
- `config.yaml`의 `denoise_mode: auto` 설정 시, 오디오 품질 메트릭(SNR, noise_level)에 따라 `off` / `mild` / `moderate` 자동 선택
- 레퍼런스 분석 시와 사용자 오디오 평가 시 독립적으로 적용

### 4.6 Difficulty Scoring (난이도 산정)
각 파트에 대해 4가지 요소를 합산하여 `Easy` / `Normal` / `Hard` / `Expert` 난이도 라벨을 부여:
- WPM (분당 단어 수) 점수
- 단어 수 점수
- 어휘 난이도 비율 (A1 기본 단어 제외)
- 축약형(Reduction) 출현 점수

---

## 5. 프로젝트 디렉터리 구조

```
AI/
├── main.py                  # FastAPI 앱 진입점
├── pipeline.py              # WhisperX STT, 채점 헬퍼
├── config.py / config.yaml  # 설정 관리 (300+ 파라미터)
├── schemas.py               # Pydantic 요청/응답 스키마
├── services/
│   ├── reference_service.py           # 레퍼런스 생성 (병렬/분기 관리)
│   ├── reference_translation_service.py  # Gemini LLM 번역/병합
│   ├── reference_payload.py           # 응답 페이로드 빌더
│   └── evaluation_service.py          # 사용자 평가 유스케이스
├── domain/
│   ├── processing/              # 텍스트, 품질, 오디오 기초 처리
│   │   ├── audio_processing.py  # 보컬 분리, 디노이징, Peak 정규화
│   │   ├── quality.py           # 오디오 품질 추정, 품질 게이트
│   │   ├── text_processing.py   # 문장/턴 분할, 짧은 파트 병합
│   │   ├── speaker_analysis.py  # 화자 분석, diarization 후처리
│   │   ├── engine_utils.py      # 유틸리티 (케노니컬 토큰 등)
│   │   └── constants.py         # A1 단어 사전, 축약형 패턴
│   ├── prosody/                 # F0/RMS 피처 추출 엔진
│   │   ├── feature_extraction.py # Baseline 0 Fixed + Smart Crop 추출
│   │   └── source_selection.py  # 원본 vs VR 소스 게이팅 결정 트리
│   ├── scoring/                 # 사용자 발화 채점 엔진 (8개 모듈)
│   │   ├── aggregator.py        # 평가 오케스트레이션 및 점수 통합
│   │   ├── prosody_scoring.py   # Hybrid: Pearson 유사도 + DTW 타이밍
│   │   ├── boundary_scoring.py  # 종결 억양 채점
│   │   ├── rhythm_scoring.py    # 단어별 리듬 채점
│   │   └── ...                  # pause, dynamic, pitch_contour, word_alignment
│   └── stt/
│       └── diarization.py       # Pyannote 화자 분리
├── integrations/
│   ├── youtube_service.py    # YouTube 오디오/자막 다운로드
│   ├── io_utils.py           # S3/URL 다운로드, 파일 관리
│   └── audio_cache.py        # 오디오 다운로드 LRU+TTL 캐시
└── test/
    ├── test_api.py           # API 테스트 CLI
    ├── test_vr_onnx.py       # VR 분리 테스트
    ├── visualize_prosody_eval_mode.py  # 유저 평가 데이터 프리미엄 시각화 (6종 PNG)
    └── visualize_prosody_vr_mode.py    # VR 음원 비교 시각화
```
