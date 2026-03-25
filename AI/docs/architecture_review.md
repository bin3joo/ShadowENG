# StyleEcho AI 코드베이스 아키텍처 리뷰

> 분석 일시: 2026-03-24  
> 대상: `AI/` 디렉터리 전체 (Python 코드 약 5,800줄)

---

## 1. 현재 프로젝트 구조 요약

```
AI/
├── main.py                  (9줄)   — 진입점 (importlib 기반)
├── pipeline.py              (1,555줄) ⚠️ — God Object, 핵심 분리 대상
├── config.py                (352줄)  — OmegaConf YAML 기반 설정 로더 + 상수 나열
├── schemas.py               (261줄)  — Pydantic 요청/응답 스키마
│
├── api/                              — FastAPI 라우트 (얇은 계층, 양호)
│   ├── app.py               (88줄)
│   ├── evaluation.py        (33줄)
│   ├── reference.py         (33줄)
│   └── system.py            (34줄)
│
├── services/                         — 유스케이스 서비스
│   ├── reference_service.py (565줄) ⚠️ — 오케스트레이션 과다
│   ├── reference_payload.py (265줄)
│   ├── reference_translation_service.py (606줄)
│   └── evaluation_service.py (90줄)
│
├── domain/processing/                — 도메인 로직 (가장 잘 분리된 계층)
│   ├── audio_processing.py  (221줄)
│   ├── text_processing.py   (534줄)
│   ├── speaker_analysis.py  (303줄)
│   ├── quality.py           (422줄)
│   ├── engine_utils.py      (156줄)
│   └── constants.py         (251줄)
│
├── integrations/                     — 외부 서비스 연동
│   ├── youtube_service.py   (213줄)
│   ├── audio_cache.py       (232줄)
│   └── io_utils.py          (241줄)
│
└── config.yaml / config_default.yaml — 런타임 설정
```

**총 파일 크기 분포:**  
- `pipeline.py` 57KB (1,555줄) — 전체 Python 코드의 **~27%**
- `services/` 합산 1,526줄
- `domain/processing/` 합산 1,887줄

---

## 2. 핵심 문제: `pipeline.py` God Object

### 2.1 현황

`StyleEchoPipeline` 클래스가 **1,280줄** 이상이며 아래 **6개 이상의 독립 관심사**를 하나의 클래스에 포함합니다:

| 관심사 | 메서드 | 줄 수 (대략) |
|--------|--------|:---:|
| WhisperX STT + 강제 정렬 | `extract_whisper_stats`, `align_text_to_audio` | ~180 |
| Diarization 로드/적용 | `_load_diarization_model`, `_apply_diarization` | ~130 |
| 억양 특징 추출 (F0/RMS) | `extract_prosody_features` | ~40 |
| 종결 억양 분석 | `analyze_boundary_tone` | ~90 |
| 동적 강세 분석 | `analyze_dynamic_stress` | ~35 |
| 단어 정렬 | `_align_user_words_to_ref` | ~80 |
| 단어 리듬 분석 | `analyze_word_rhythm` | ~100 |
| 억양 유사도 DTW | `analyze_prosody` | ~15 |
| 피치 컨투어 피드백 | `analyze_word_pitch_contour` | ~70 |
| 위치 기반 Pause 정합 | `analyze_pause_alignment` | ~60 |
| **종합 평가 (evaluate)** | `evaluate` | **~235** |

또한 **모듈 최상위에 6개 독립 함수**(`_normalize_f0`, `_build_f0_gate_metrics`, `_build_rms_gate_metrics`, `select_reference_prosody_sources`, `_empty_stats`, `get_pipeline`)가 클래스와 무관하게 존재합니다.

### 2.2 문제점

1. **단일 책임 원칙(SRP) 위반** — STT, 특징 추출, 채점 알고리즘, 모델 관리가 하나의 클래스에 혼재
2. **테스트 어려움** — WhisperX GPU 모델을 로드하지 않으면 채점 로직만 단독 테스트 불가
3. **재사용 불가** — `analyze_boundary_tone` 같은 순수 계산 함수가 클래스 인스턴스에 묶여 있음
4. **import 지연** — `evaluate()` 내부에서 `import tempfile`, `import os`, `from scipy.io import wavfile` 등 런타임 import 존재

---

## 3. 제안: `pipeline.py` 분리 방안

### 3.1 목표 구조

```
AI/
├── pipeline.py              — 얇은 Facade (STT 호출 + 평가 위임만)
│
├── domain/
│   ├── processing/          — (기존 유지)
│   │   ├── audio_processing.py
│   │   ├── text_processing.py
│   │   ├── speaker_analysis.py
│   │   ├── quality.py
│   │   ├── engine_utils.py
│   │   └── constants.py
│   │
│   ├── stt/                 — ✨ 신규: STT 및 정렬 관심사
│   │   ├── whisperx_stt.py       — extract_whisper_stats, align_text_to_audio
│   │   └── diarization.py        — _load_diarization_model, _apply_diarization
│   │
│   ├── prosody/             — ✨ 신규: 억양/에너지 특징 추출 및 분석
│   │   ├── feature_extraction.py — extract_prosody_features, _normalize_f0
│   │   ├── source_selection.py   — select_reference_prosody_sources, gate 함수들
│   │   └── analysis.py           — analyze_boundary_tone, analyze_dynamic_stress
│   │
│   └── scoring/             — ✨ 신규: 채점 로직 (순수 함수)
│       ├── word_scoring.py       — WER 기반 단어 정확도
│       ├── speed_scoring.py      — 속도 점수 (deadband)
│       ├── pause_scoring.py      — 멈춤 횟수 + F1 정합
│       ├── rhythm_scoring.py     — analyze_word_rhythm
│       ├── prosody_scoring.py    — analyze_prosody (DTW)
│       ├── pitch_contour.py      — analyze_word_pitch_contour
│       ├── word_alignment.py     — _align_user_words_to_ref
│       └── aggregator.py         — 가중 종합 점수 계산
```

### 3.2 분리 우선순위

| 우선순위 | 분리 대상 | 이유 | 난이도 |
|:---:|---|---|:---:|
| **P0** | `evaluate()` → `scoring/aggregator.py` | 가장 긴 메서드(235줄), 순수 계산 로직 | 중 |
| **P0** | `analyze_*` 메서드들 → `scoring/*.py` | `self` 미사용 또는 최소 사용, 순수 함수화 가능 | 하 |
| **P1** | STT 메서드 → `domain/stt/` | WhisperX 의존성 격리 → 채점 로직 단독 테스트 가능 | 중 |
| **P1** | Prosody 추출 → `domain/prosody/` | librosa/pyin 의존성 격리 | 하 |
| **P2** | Diarization → `domain/stt/diarization.py` | 조건부 로드 로직이 복잡, 단독 관리 필요 | 하 |

### 3.3 분리 후 `pipeline.py` 역할

```python
class StyleEchoPipeline:
    """얇은 Facade — 모델 인스턴스를 보유하고 하위 모듈에 위임."""

    def __init__(self, ...):
        self.stt = WhisperXSTT(...)       # domain/stt/
        self.prosody = ProsodyExtractor()  # domain/prosody/

    def evaluate(self, user_audio_path, ref_data):
        return evaluate_user_audio(       # domain/scoring/aggregator.py
            stt=self.stt,
            prosody=self.prosody,
            user_audio_path=user_audio_path,
            ref_data=ref_data,
        )
```

**예상 결과:** `pipeline.py`가 ~100줄 이하로 축소, 각 하위 모듈이 200줄 이내로 유지

---

## 4. `services/reference_service.py` 오케스트레이션 과다

### 4.1 현황

`generate_reference()` 함수가 **단일 함수 내에 370줄** 이상이며:
- YouTube 다운로드/캡션 가져오기
- STT or Caption Align 분기
- Vocal Separation
- 단어 정제 및 리베이스
- Prosody 추출 (원본 vs VR 분기)
- 문장 분할 + 번역
- 품질 평가 + 거부 판정
- 오디오 저장 + 파트 export
- 에러 시 클린업

### 4.2 개선 방안

```
generate_reference()  # 현재: 370줄 단일 함수
    ↓
┌─ _download_and_prepare()     — YouTube 다운로드, 캡션, VR 분기
├─ _transcribe_and_align()     — STT 또는 Caption Align + fallback
├─ _extract_prosody()          — 원본/VR/both 분기 로직
├─ _build_reference_payload()  — 문장 분할, 번역, 품질 평가
└─ _persist_artifacts()        — 오디오 저장, 파트 WAV export
```

각 단계를 **private 함수로 분리**하면:
- 단위 테스트 작성 가능
- 에러 발생 지점 추적 용이
- 코드 리뷰 시 각 단계 독립적으로 검토 가능

---

## 5. `config.py` 상수 폭발 문제

### 5.1 현황

352줄, **90개 이상의 모듈 레벨 상수**가 하나의 파일에 나열. 상수 이름에 prefix를 붙여 그룹핑을 시도했으나 여전히 flat 구조.

### 5.2 개선 방안

**Option A: 네임스페이스 객체 (권장)**

```python
# config.py
@dataclass(frozen=True)
class ScoringConfig:
    wer_penalty: float
    speed_k: float
    ...

@dataclass(frozen=True)
class ReferenceConfig:
    denoise_mode: str
    enable_diarization: bool
    ...

scoring = ScoringConfig(**{...from yaml...})
reference = ReferenceConfig(**{...from yaml...})
```

사용처: `config.scoring.wer_penalty` → 가독성 향상, IDE 자동완성 지원

**Option B: 현행 유지 + 그룹 주석 강화** — 최소 변경으로 유지하되, 사용처 grep이 어려운 문제 지속

---

## 6. 기타 개선 사항

### 6.1 `schemas.py` 분리

현재 261줄에 **Request + Response + 중간 DTO** 스키마가 모두 혼재.

```
schemas/
├── reference.py    — GenerateReferenceRequest/Response, ReferencePartData 등
├── evaluation.py   — EvaluateAudioRequest/Response, EvaluateScores 등
└── common.py       — WordTimestamp, ProsodyFeatures (공유 타입)
```

### 6.2 `evaluate()` 내부 런타임 import 제거

```python
# pipeline.py:1322-1324 (현재)
def evaluate(self, ...):
    import tempfile
    from domain.processing.audio_processing import peak_normalize_audio
    ...
    from scipy.io import wavfile
```

→ **파일 상단으로 이동** 필요. 런타임 import는 순환 import 방지 목적이 아니면 안티패턴.

### 6.3 `reference_translation_service.py` Pydantic 모델 분리

606줄 중 약 80줄이 `LearningExpression`, `GeminiMergedPart` 등 Pydantic 모델 정의. 이것들은 `schemas/` 또는 `domain/models/` 로 분리하여 재사용성 확보.

### 6.4 싱글턴 패턴 통합

현재 **3곳**에서 독립적으로 싱글턴 패턴을 구현:
- `pipeline.py` → `_pipeline_instance` + Lock
- `audio_cache.py` → `_cache_instance` + Lock
- `reference_translation_service.py` → `_gemini_client` + Lock

→ 공통 `utils/singleton.py` 또는 **DI 컨테이너** 도입 고려

### 6.5 `_` prefix 함수의 외부 참조

`domain/processing/` 내부에서 `_` prefix (내부 사용 의도) 함수들이 외부 모듈에서 직접 import 되는 경우가 많음:
- `_canonicalize_tokens`, `_normalize_word`, `_REMOVE_PUNCT` → `pipeline.py`에서 사용
- `_dominant_speaker_label`, `_get_word_speaker_label` → `reference_translation_service.py`에서 사용
- `_build_reference_part` → `reference_translation_service.py`에서 사용

→ 외부에서 사용되는 함수는 **`_` prefix를 제거**하거나, 해당 모듈의 `__all__`에 명시적으로 포함

---

## 7. 잘 설계된 부분 (유지 권장)

| 항목 | 설명 |
|------|------|
| **api/ 라우트 계층** | 매우 얇고 깔끔. `run_in_threadpool`로 blocking 작업 위임 |
| **domain/processing/ 분리** | `text_processing`, `speaker_analysis`, `quality` 등 관심사별 모듈 분리가 잘 되어 있음 |
| **OmegaConf 기반 설정** | YAML deep merge, 기본값/사용자 설정 오버라이드 패턴이 적절 |
| **AudioCache** | 스레드 안전, LRU+TTL 제거 전략, 싱글턴 패턴 — 잘 구현됨 |
| **reference_payload.py** | 페이로드 빌드/정제 로직이 서비스에서 분리됨 |
| **BackgroundTasks 활용** | 임시 파일 정리를 응답 후 비동기 처리 |

---

## 8. 개선 로드맵 (우선순위 순)

| 단계 | 작업 | 영향도 | 난이도 | 예상 효과 |
|:---:|------|:---:|:---:|------|
| **1** | `pipeline.py`의 `analyze_*` 순수 함수들을 `domain/scoring/`으로 분리 | 높음 | 하 | 단독 테스트 가능, 파일 크기 60% 감소 |
| **2** | `evaluate()` 메서드를 `domain/scoring/aggregator.py`로 추출 | 높음 | 중 | 채점 로직 독립 테스트 가능 |
| **3** | STT/Diarization 로직을 `domain/stt/`로 분리 | 높음 | 중 | GPU 의존성 격리 |
| **4** | `generate_reference()` 내부를 단계별 private 함수로 분해 | 중간 | 하 | 가독성/유지보수성 대폭 향상 |
| **5** | `_` prefix 함수 중 외부 사용분 → public API로 승격 | 낮음 | 하 | 인터페이스 명확화 |
| **6** | `config.py` 네임스페이스 객체 도입 | 낮음 | 중 | IDE 자동완성, 타입 안전 |
| **7** | `schemas.py` 도메인별 분리 | 낮음 | 하 | 파일 크기 축소, 응집도 향상 |

---

## 9. 의존성 흐름 (현재 → 개선 후)

### 현재
```
api/ → services/ → pipeline.py (God Object)
                 → domain/processing/
                 → integrations/
```

### 개선 후
```
api/ → services/ → domain/stt/        (STT 전용)
                 → domain/prosody/     (특징 추출)
                 → domain/scoring/     (채점 로직)
                 → domain/processing/  (텍스트/화자/품질)
                 → integrations/       (YouTube/IO/Cache)
     
pipeline.py = 얇은 Facade (모델 인스턴스 보유 + 위임)
```

**핵심 원칙:** 순수 계산 로직(채점/분석)은 GPU 모델과 분리하여 단독 테스트·재사용 가능하게

---

## 10. 리팩터링 진행 현황

> 최종 업데이트: 2026-03-24

### 10.1 완료된 작업

| # | 작업 | 상태 | 비고 |
|:---:|------|:---:|------|
| 1 | `pipeline.py` `analyze_*` 순수 함수 → `domain/scoring/` 분리 | ✅ | 7개 모듈 생성 |
| 2 | `evaluate()` → `domain/scoring/aggregator.py` 추출 | ✅ | `stt_fn`, `prosody_fn` 콜백 주입 |
| 3 | STT/Diarization → `domain/stt/diarization.py` | ✅ | 로드/적용 함수 분리 |
| 3b | Prosody 추출 → `domain/prosody/feature_extraction.py` | ✅ | `_normalize_f0` 포함 |
| 3c | Source Selection → `domain/prosody/source_selection.py` | ✅ | gate 함수 포함, 하위 호환 re-export 유지 |
| 4 | `generate_reference()` 단계별 분해 | ✅ | 4개 private 함수로 분리 |
| 5 | `_` prefix 함수 외부 사용분 승격 | ⏭️ | 사용자 요청으로 건너뜀 |
| 6 | `config.py` 네임스페이스 객체 도입 | ⏭️ | 사용자 요청으로 보류 |
| 7 | `schemas.py` 도메인별 분리 | ⏭️ | 보류 (low priority) |

### 10.2 생성된 신규 파일

```
domain/
├── scoring/                    — ✅ 채점 순수 함수 패키지
│   ├── __init__.py
│   ├── boundary_scoring.py     — analyze_boundary_tone
│   ├── dynamic_scoring.py      — analyze_dynamic_stress
│   ├── word_alignment.py       — align_user_words_to_ref
│   ├── rhythm_scoring.py       — analyze_word_rhythm
│   ├── prosody_scoring.py      — analyze_prosody (DTW)
│   ├── pitch_contour.py        — analyze_word_pitch_contour
│   ├── pause_scoring.py        — analyze_pause_alignment
│   └── aggregator.py           — evaluate (종합 평가)
│
├── stt/                        — ✅ Diarization 분리
│   ├── __init__.py
│   └── diarization.py          — load/apply diarization
│
└── prosody/                    — ✅ 억양 특징 추출/소스 선택
    ├── __init__.py
    ├── feature_extraction.py   — extract_prosody_features, _normalize_f0
    └── source_selection.py     — select_reference_prosody_sources, gate 함수들
```

### 10.3 파일 크기 변화

| 파일 | Before | After | 변화 |
|------|:---:|:---:|:---:|
| `pipeline.py` | 1,555줄 | ~500줄 | **-68%** |
| `reference_service.py` | 565줄 | ~650줄 (함수 분리로 docstring 증가) | 가독성 대폭 향상 |

### 10.4 남은 과제 (향후)

- **`config.py` 네임스페이스 객체**: IDE 자동완성 및 타입 안전 강화 (보류 중)
- **`schemas.py` 도메인별 분리**: `schemas/reference.py`, `schemas/evaluation.py`, `schemas/common.py`
- **싱글턴 패턴 통합**: 공통 유틸 또는 DI 컨테이너 도입
- **`_` prefix 함수 정리**: 외부 사용 함수 public API 승격
