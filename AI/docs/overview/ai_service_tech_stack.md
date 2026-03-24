# AI 서비스 기술 스택 및 기능 명세

## 1. 개요 및 목적
* **목표**: YouTube 영상 구간을 기반으로 영어 말하기 연습용 '레퍼런스'를 자동 생성하고, 사용자의 발화를 레퍼런스와 1:1로 비교 분석하여 발음, 억양, 리듬 등을 정밀하게 평가하는 AI Worker 서버.
* **아키텍처**: 클라이언트/주 서버의 요청을 받는 비동기 기반의 Python **FastAPI** 서버 형태로 구축되어 있으며, AI 모델 구동을 위해 PyTorch 생태계를 적극 활용.

---

## 2. 주요 기술 스택 (Tech Stack)

### 2.1 Web & 인프라
* **Framework**: `FastAPI` (고성능, 비동기 I/O 기반 웹 프레임워크), `Uvicorn`
* **Configuration**: `OmegaConf` (딥 머지를 통한 유연한 환경 및 설정 관리), `PyYAML`
* **Storage IO**: AWS S3 인터페이스 (`boto3`)

### 2.2 Core AI & NLP 모델
* **STT & Alignment**: `WhisperX` (`faster-whisper` 코어 활용)
  * 높은 텍스트 변환 정확도 뿐만 아니라, 화자 발화의 **단어 단위 정밀 타임스탬프(Word-level timestamps)** 추출 제공.
* **Speaker Diarization (화자 분할)**: `pyannote.audio`
  * 여러 명의 화자가 등장하는지 파악하고, 각 화자가 말하는 구간 분할.
* **LLM (번역 및 메타정보 추출)**: `google-genai` (Gemini API)
  * 다국어 번역 수행 및 교육 목적에 맞는 영어 '주요 학습 표현' 자동 추출 지원. 자연어 처리(NLP) 기반 컨텍스트 파악.
* **Audio Separation (보컬/배경 제거)**: 보컬 분리(VR) 파이프라인 (기반: `audio-separator`)

### 2.3 Audio Processing & Analysis
* **음성 신호 처리**: `librosa`, `numpy`, `scipy`
  * 소리 크기(RMS Energy), 피치 곡선(F0 Contour), 프레임 단위 처리(Hop Length) 추출용 코어 라이브러리.
* **신호/텍스트 유사도 매칭 (평가)**:
  * `fastdtw`: 두 다차원 시계열 오디오 특성(피치, RMS)의 곡선 유사도를 구하는 Dynamic Time Warping 알고리즘.
  * `jiwer`: WER(Word Error Rate) 평가를 통한 발음 정확도 측정.
  * `noisereduce`, `pydub`: 음원 전처리/정제용.

### 2.4 External Integrations
* **Media Extraction**: `yt-dlp` (유튜브 영상 음원 추출), `youtube-transcript-api` (공식 자막 추출)

---

## 3. 핵심 사용 기능

### 3.1 레퍼런스 생성 API (`generate-reference`)
YouTube `video_id`와 재생 구간(시작/종료)을 입력하면 영어 학습용 교보재(Reference) 데이터를 생성합니다. 
1. **오디오 및 자막 병렬 추출**: 자막을 받음과 동시에 음원 파일 다운로드 병렬 수행.
2. **Fast Path Alignment**: 이미 있는 YouTube 자막을 활용하여 빠른 Align 우선 시도 후, 실패 시 `WhisperX STT` 모델을 사용한 Fallback 처리.
3. **학습 단위 분리**: 화자의 짧은 휴지기(Pause)나 문장 부호(Punctuation) 기준으로 긴 발언을 문장/턴(Dialog Turn) 단위 Part로 분리. 단, 과도하게 짧은 파트는 병합.
4. **번역 및 학습 메타데이터 생성**: 파트별 영어 텍스트를 Gemini를 활용해 한국어로 번역하고, 주요 표현(Learning expressions) 추출.

### 3.2 유저 발화 평가 API (`evaluate-audio`)
학습자가 녹음한 오디오(`user_audio`)를 이미 구축된 레퍼런스와 비교해 점수화합니다.
1. 사용자의 녹음 파일(로컬 단말 전송 혹은 S3 링크)을 수신.
2. 원어민(레퍼런스)의 음성 피처(Word Timestamp, RMS, F0) 데이터와 시간 축 매칭.
3. 단어별 정확도, 억양 유사도, 속도 및 휴지기(Pause) 일치도를 종합하여 정량화된 100점 만점 기준의 종합 점수 반환.

---

## 4. AI 특화 세부 기능 (Core AI Features)

* **보컬 전경화 및 소스 게이팅 (Vocal Remover / VR Gating)**
  * 비디오 원본 오디오에 배경음악/노이즈가 깔려 있으면 억양(Pitch) 비교 정확도가 크게 하락함.
  * 따라서 배경음악과 보컬을 딥러닝으로 상호 분리(VR) 하여, 노이즈가 제거된 '순수 화자 발화' 채널 상에서 F0, RMS 등을 추출. 이때 최적의 품질을 갖기 위해 원본 오디오 스코어와 VR 오디오 스코어를 비교 평가해(Quality Gating) 결정됨.
* **단어 단위 정밀 Prosody(운율) 비교 (DTW 매칭)**
  * 원어민이 말한 단어 길이는 0.8초, 학습자가 말한 단어 길이는 1.2초 식으로 전체 수행 기간이 달라도, `FastDTW` 알고리즘을 통해 억양(시계열 피치 곡선)과 강세(에너지 흐름)가 형태적으로 얼마나 유사한지 분석해 점수 도출.
* **Boundary Tone 분석 (종결 억양 평가)**
  * 각 문장/단어의 뒤 끝 억양이 어떻게 맺어지는지(ex: 의문문에서 끝이 올라가는지) 경사도 측정(Slope Bias) 알고리즘으로 비교 피드백 제공.
* **Speaker Diarization & Risk Assessment (화자 분석 및 품질 통제)**
  * `pyannote.audio`를 통해 가져온 음성 내에 발화자가 1명인지 집중적으로 측정.
  * 여러 명이 떠들어서 학습용 스크립트로 사용할 수 없는 "Multi-speaker Risk"가 높을 때는, 레퍼런스 품질 판정 모듈이 최종적으로 학습 `reject` 코드를 리턴하여 불량 구간 학습을 사전에 차단.
