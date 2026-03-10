# StyleEcho — 파이썬 서버 기능 분석 및 AI 적용 제안

> 최종 업데이트: 2026-03-06  
> 기준: `pipe/engine.py` + `pipe/main.py` + `pipe/config.py` + `pipe/constants.py` 현재 구현 상태  
> **code_review_2026-03-05.md 전 항목 수정 반영됨**

---

## 1. 현재 파이썬 서버가 처리하는 기능 ✅

| 기능 ID | 기능명 | 처리 방식 | 상태 |
|---------|--------|-----------|------|
| CONT-004 | 전사 생성 | WhisperX STT + Forced Alignment | ✅ 구현 |
| CONT-005 | 리듬·강세 분석 | librosa F0/RMS + FastDTW | ✅ **Two-Track 디노이징 적용** |
| STUDY-003 | 발화 평가 (7대 지표) | WER + DTW + 선형회귀 + CV | ✅ 구현 |
| STUDY-004 | 오류 하이라이팅 (데이터) | word_level_feedback (rushed/dragged/missed) | ✅ 구현 |
| STUDY-007 | 분석 리포트 (데이터) | evaluate(user_audio, ref_data) JSON 반환 | ✅ **리팩터링됨** |
| CONT-003 | 구간 선택 + 전처리 | **trim_boundary_fragments()** | ✅ 구현 |
| STUDY-002 | 한국어 자막 | **Helsinki-NLP/opus-mt-en-ko** 번역 | ✅ 구현 |
| —        | 문장 단위 파싱 | **split_into_sentences_with_timestamps()** | ✅ 구현 |
| —        | 난이도 자동 분류 | 문장별 WPM + 어휘 + 연음 패턴 스코어링 | ✅ 구현 |
| —        | 구어체 연음 감지 | REDUCTION_PATTERNS 사전 기반 (30종) | ✅ 구현 |
| —        | YouTube 캡션 Fast Path | caption_align (STT 건너뛰기, ~10x 빠름) | ✅ 구현 |
| —        | 단어별 피치 컨투어 피드백 | **analyze_word_pitch_contour()** | ✅ 구현 |
| —        | 연음 통일 (Word Alignment) | **_align_user_words_to_ref()** | ✅ **신규 구현** |
| —        | YAML 기반 설정 관리 | **config.py + config_default.yaml** | ✅ 구현 |
| —        | 언어 상수 분리 | **constants.py** (REDUCTION_PATTERNS, A1_WORDS) | ✅ **신규 구현** |
| —        | 파트별 F0/RMS 분리 저장 | **parts[].features** | ✅ 구현 |

---

## 2. 스코어링 수식 현황

| 지표 | 수식 | 범위 | 비고 |
|------|------|------|------|
| **word_accuracy** | `100 * exp(-2.5 * WER)` | 8~100 | 지수 감쇠, 0점 미만 불가 |
| **prosody** | `100 * exp(-β * DTW_dist)` | 0~100 | FastDTW 거리 기반 |
| **speed** | `100 * ratio^(k*rushing)` 또는 `100 * (1/ratio)^k` | 0~100 | 비대칭 페널티 (rushing 시 강화) |
| **pause** | `100 * exp(-diff²/2σ²)` | 0~100 | ref word gap 기반 실측 계산 |
| **rhythm** | `100 * mean(word_rd_ratios)` | 0~100 | missed 단어 평균 제외 |
| **boundary_tone** | semitone slope 방향/강도 비교 | 40~100 | 화자 독립적 |
| **dynamic_stress** | CV 비율 비교 | 0~100 | 변동계수 비율 |

> 모든 스코어가 `0점 이하로 내려가기 어려운` 지수/비율 기반 수식으로 통일됨

---

## 3. 파이썬 서버 미적용 기능

### 3-1. 🔴 음성 분석 적용 안됨 (Python 서버에서 처리 가능)

#### STUDY-005 — 표현 정보 제공 (발음 설명)
**현재:** 없음  
**방법 A:** `nltk.corpus.cmudict` → IPA/음소 변환 (속도 빠름, 오프라인)  
**방법 B:** Wav2Vec2 기반 음소 분류기 (→ AI-01 참조)

---

#### REVIEW-003 — 과거 비교 분석
**현재:** 없음  
**제안:** Java 서버가 과거 scores JSON을 함께 전달 → Python에서 delta 계산

```
POST /api/v1/compare-progress
{ "current_result": {...}, "previous_result": {...} }
→ { "score_delta": {...}, "improved_words": [...], "regressed_words": [...] }
```

---

#### CONT-001 — 문장 검색
**현재:** 없음  
**제안:** `youtube-transcript-api` → 자막 텍스트 검색 → timestamp 추출

```
POST /api/v1/search-segment
{ "youtube_url": "...", "query": "I had this meeting" }
→ { "matches": [{ "start_sec": 30.1, "end_sec": 45.0, "matched_text": "..." }] }
```

---

### 3-2. 🟡 Java/프론트 영역이지만 Python 데이터 품질 개선 필요

| 기능 ID | 기능명 | 개선 사항 |
|---------|--------|-----------|
| STUDY-006 | 북마크 기능 | Python이 score 낮은 단어 자동 플래그 반환 |
| CHAL-001 | 공통 문장 제공 | Python `difficulty` 필드로 난이도 자동 태깅 ✅ 구현 |
| MYP-004 | 리포트 조회 | Python에서 집계 통계 계산 엔드포인트 |

---

## 4. AI 적용 가능 파트

### 🔵 AI 적용 강력 추천

#### AI-01. 음소 수준 발음 오류 감지 (Wav2Vec2 / HuBERT)
**대상:** STUDY-003, STUDY-004, STUDY-005  
**현재 한계:** `jiwer.wer()`은 단어 단위 → "meeting"을 "meding"으로 발음해도 텍스트 같으면 100점  
**모델:** `facebook/wav2vec2-base-960h`  
**예상 응답 추가:**
```json
"phoneme_feedback": [
  { "word": "this", "expected": ["DH","IH","S"], "got": ["D","IH","S"],
    "errors": [{"position": 0, "expected": "DH", "got": "D"}] }
]
```

---

#### AI-02. 자동 난이도 분류 ✅ 구현됨
**현재:** 텍스트 + WPM 기반 규칙형 난이도 분류 (`parts[].difficulty`) 구현완료  
**추가 가능:** F0 분산(억양 복잡도), 연음 빈도를 추가 반영 → 이미 `reduction_score` 포함됨  
**미구현:** 딥러닝 기반 CEFR 분류기

---

#### AI-03. 연음/축약 패턴 감지 ✅ 구현됨
**구현 방법:** `REDUCTION_PATTERNS` 사전 + `parts[].reductions` 반환  
**30종 패턴 포함:** gonna, wanna, gotta, gotcha, kinda, gimme, lemme, c'mon, 단축형 등

---

#### AI-04. 단어별 피치 컨투어 피드백 ✅ 신규 구현
**구현 방법:** `analyze_word_pitch_contour()` — 각 단어 구간의 F0 전반부/후반부 평균 비교  
**피드백 유형:**
| feedback | 의미 |
|----------|------|
| `good` | 피치 방향 일치 |
| `raise_end` | 끝에서 올려야 함 (레퍼런스는 rising) |
| `lower_end` | 끝에서 내려야 함 (레퍼런스는 falling) |
| `more_emphasis` | 같은 방향이지만 강도 부족 |

---

#### AI-05. 한국어 번역 ✅ 신규 구현
**모델:** `Helsinki-NLP/opus-mt-en-ko` (HuggingFace Transformers)  
**적용 위치:**
- `generate-reference` → `final_script_ko`, `parts[].sentence_ko`
- 지연 로드 (첫 호출 시 모델 다운로드, 이후 캐시)
- CPU 전용 (번역은 가벼움, GPU 불필요)

---

#### AI-06. 개인화 난이도 적응 (SM-2 간격 반복)
**대상:** REVIEW-001, CHAL-001  
**제안:**
```
POST /api/v1/calculate-next-review
{ "word": "meeting", "history_scores": [45, 62, 70, 85] }
→ { "next_review_days": 3, "stability": 0.7 }
```

---

### 🟡 AI 적용 검토 (중기)

| 파트 | AI 기법 | 기대 효과 |
|------|---------|-----------| 
| 북마크 자동 추천 | 낮은 점수 단어 + CEFR 중요도 가중 | 학습 효율 |
| 발화 자연스러움 | MOS 예측 모델 | 전체 품질 수치화 |

---

## 5. 구현 로드맵 (현재 상태)

```
Phase 1 ✅ 완료
├── WhisperX STT + Forced Alignment
├── 7대 지표 채점 (WER→지수감쇠, DTW, F0, RMS, 리듬, 속도, 멈춤)
├── Two-Track 디노이징 (noisereduce Spectral Gating)
├── trim_boundary_fragments() (구간 경계 정제)
├── split_into_sentences_with_timestamps() (문장 파싱 + 난이도)
├── 연음/축약 패턴 감지 (AI-03 완료)
├── YouTube 캡션 Fast Path (caption_align)
├── 파트별 F0/RMS 분리 저장 + 개별 평가
├── YAML 기반 설정 관리 (config_default.yaml)
├── 한국어 번역 (AI-05 Helsinki-NLP/opus-mt-en-ko)
├── 단어별 피치 컨투어 피드백 (AI-04)
├── Pydantic 모델 전면 정비 + response_model 적용
├── evaluate() 통합 리팩터링 (ref_data JSON 기반, ref_audio 불필요)
├── _align_user_words_to_ref() 연음 통일 파이프라인
├── constants.py 언어 상수 분리
├── code_review 전 항목 수정 (BUG-01~08, MATH-01~07, PERF-01~03, QUAL-01~03)
├── pause_score ref word gap 기반 실측 계산
├── speed_score 비대칭 페널티 + boundary_tone semitone 기반
├── FastAPI lifespan + tmp_dir 정리 + URL 다운로드 타임아웃
└── FastDTW radius + 정규식/구두점 제거기 재사용 + 오디오 중복 로드 완화

Phase 2 (단기 권장)
├── /api/v1/search-segment (youtube-transcript-api)
└── /api/v1/compare-progress (과거 비교 delta)

Phase 3 (중기)
├── AI-01: Wav2Vec2 음소 오류 감지
└── AI-06: SM-2 적응형 복습 스케줄링

Phase 4 (장기)
└── 발화 자연스러움 MOS 예측
```

---

## 6. 파일 구조

```
pipe/
├── __init__.py           # 패키지 초기화
├── main.py               # FastAPI 앱 (API 레이어만 담당)
├── engine.py             # 핵심 파이프라인 (STT, 분석, 채점)
├── config.py             # YAML 설정 로더 + 하위 호환 상수
├── config_default.yaml   # 기본 설정값
├── constants.py          # 언어 상수 (REDUCTION_PATTERNS, A1_WORDS)
└── requirements.txt      # 의존성
```

---

## 7. API 엔드포인트 명세

### `POST /api/v1/generate-reference`
**Request:** `GenerateReferenceRequest` (youtube_url, start_sec, end_sec)  
**Response:** `GenerateReferenceResponse`
- `parts[]`: 문장별 데이터 (sentence, sentence_ko, features, word_timestamps, difficulty)
- `final_script`, `final_script_ko`: 전체/한국어 스크립트

### `POST /api/v1/evaluate-audio`
**Request:** `EvaluateAudioRequest` (user_audio URL/base64, final_script, features, word_timestamps)  
**Response:** `EvaluateAudioResponse`
- `scores`: 7대 지표
- `details`: word_level_feedback, boundary_tone, dynamic_stress, pitch_contour_feedback
- 내부: `evaluate(user_audio_path, ref_data)` 단일 진입점 (ref_audio 불필요)

### ~~`POST /api/v1/evaluate-chunk`~~ — 삭제됨

### (미구현) `POST /api/v1/search-segment`
### (미구현) `POST /api/v1/compare-progress`
