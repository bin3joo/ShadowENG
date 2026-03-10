# StyleEcho 코드 리뷰 및 개선 제안

> 날짜: 2026-03-05  
> 대상: `pipe/` 전체 (engine.py, main.py, config.py, constants.py, config_default.yaml)  
> **최종 동기화: 2026-03-06 — 전 항목 수정 완료**

---

## 1. 버그 및 오류

### BUG-01. `os` import 미사용 (engine.py) ✅ 수정됨
**위치:** `engine.py` line 14  
**내용:** `import os`가 선언되어 있으나 파일 내 어디에서도 사용되지 않음  
**수정:** `import os` 제거  
**심각도:** 낮음 (동작에 영향 없음, 코드 위생)

---

### BUG-02. `jiwer.RemovePunctuation()` 반복 인스턴스화 (engine.py) ✅ 수정됨
**위치:** `_align_user_words_to_ref`, `analyze_word_rhythm`, `analyze_word_pitch_contour`  
**내용:** 매 루프 반복마다 `jiwer.RemovePunctuation()` 인스턴스를 새로 생성함. 상태 없는 변환기이므로 모듈 수준 상수로 한 번만 생성해야 함  
**수정안:**
```python
# engine.py 상단 (logger 아래)
_REMOVE_PUNCT = jiwer.RemovePunctuation()

# 사용 시
raw_r = _REMOVE_PUNCT(r_word.get("word", "").lower())
```
**심각도:** 중간 (성능 영향, 긴 문장에서 수십~수백 회 호출)

---

### BUG-03. `config_default.yaml` vs `config.py` 기본값 불일치 ✅ 수정됨
**위치:** `config.py` line 101, `config_default.yaml` line 34  
**내용:**
- `config.py`: `HOP_LENGTH: int = get("audio.hop_length", 512)` — fallback 기본값 **512**
- `config_default.yaml`: `hop_length: 256` — YAML 값 **256**

정상 실행 시 YAML 값(256)이 사용되지만, YAML 파일이 없으면 512가 적용되어 **F0/RMS 프레임 해상도가 2배로 달라짐**  
**수정:** `config.py` fallback 값을 256으로 통일  
**심각도:** 중간 (YAML 누락 시 채점 결과 변동)

---

### BUG-04. `tmp_dir` 미삭제 (main.py generate-reference) ✅ 수정됨
**위치:** `main.py` line 350, 442  
**내용:** `tempfile.mkdtemp()` 으로 임시 디렉토리 생성 → 오디오 파일만 `BackgroundTasks`로 삭제, **디렉토리 자체는 남음**. 반복 요청 시 빈 디렉토리가 누적됨  
**수정안:**
```python
# 파일 삭제 후 디렉토리도 삭제
import shutil
background_tasks.add_task(shutil.rmtree, tmp_dir, True)
```
**심각도:** 중간 (디스크 누수, 장기 운영 시 문제)

---

### BUG-05. `subprocess` 함수 내부 import (main.py) ✅ 수정됨
**위치:** `main.py` line 354  
**내용:** `import subprocess`가 `generate_reference()` 함수 내부에 위치. PEP 8 위반 + 매 호출마다 import 확인  
**수정:** 파일 상단으로 이동  
**심각도:** 낮음 (동작에 영향 없음, 코드 컨벤션)

---

### BUG-06. `torch` import 미사용 (main.py) ✅ 수정됨
**위치:** `main.py` line 19  
**내용:** `import torch`가 선언되어 있으나 main.py 내에서 직접 사용하지 않음 (engine.py에서 사용)  
**수정:** 제거 가능 (torchaudio 패치 코드도 engine.py에 이미 있으므로 main.py에서 중복)  
**심각도:** 낮음

---

### BUG-07. `urllib.request.urlretrieve` 타임아웃 없음 (main.py) ✅ 수정됨
**위치:** `main.py` line 547  
**내용:** S3 URL 다운로드 시 타임아웃이 없어 네트워크 문제 시 요청이 무한 대기할 수 있음. 또한 `s3://` 프로토콜은 `urlretrieve`로 처리 불가  
**수정안:** `httpx` 또는 `requests` 라이브러리 사용 + 타임아웃 설정, s3:// 별도 처리 또는 제거  
**심각도:** 중간 (프로덕션 안정성)

---

### BUG-08. `@app.on_event("startup")` 사용 (main.py) ✅ 수정됨
**위치:** `main.py` line 59  
**내용:** FastAPI 공식 문서에서 `on_event` 데코레이터는 deprecated로 명시. `lifespan` 컨텍스트 매니저 사용 권장  
**수정안:**
```python
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    get_pipeline(...)
    yield
    # shutdown (필요 시)

app = FastAPI(lifespan=lifespan, ...)
```
**심각도:** 낮음 (현재 동작엔 문제 없음, 향후 FastAPI 버전 호환)

---

## 2. 스코어링 수학적/논리적 문제점

### MATH-01. `pause_score` 항상 100점 (7.5% 가중치 낭비) ✅ 수정됨 (개선안 B 적용)
**위치:** `engine.py` evaluate() line 1155-1156  
**내용:** `ref_data`에 pause_count 정보가 없어 `pause_score = 100.0` 고정. 7.5% 가중치가 사실상 모든 유저에게 무조건 부여되어 **총점이 7.5점 인플레이션**됨  
**개선안 A:** 가중치를 다른 지표에 재분배 (pause 제거)  
**개선안 B:** ref_data에 pause_count 포함하여 실제 계산  
```yaml
# 개선안 A 가중치 예시 (합 = 1.0)
weights:
  word_accuracy: 0.30
  prosody: 0.225
  rhythm: 0.175
  boundary_tone: 0.10
  dynamic_stress: 0.10
  speed: 0.10
  # pause 제거
```

---

### MATH-02. `ref_active_time` 계산이 부정확 ✅ 수정됨
**위치:** `engine.py` evaluate() line 1139-1143  
**내용:** `ref_active_time = last_word.end - first_word.start` → 단어 사이 **쉼(pause) 구간까지 포함**. 이는 "발화 시간"이 아니라 "전체 구간 길이"  
**영향:** 
- `speed_score`: user의 active_speech_sec (pause 제외)와 ref의 total_span (pause 포함)을 비교 → 유저가 항상 불리
- `rhythm`: 상대 길이(RD) 분모가 달라서 비교 왜곡  
**개선안:** ref_data에 active_speech_sec 를 저장하거나, word duration 합산 사용
```python
ref_active_time = sum(
    w.get("end", 0) - w.get("start", 0) for w in ref_word_timestamps
)
```

---

### MATH-03. `boundary_tone` SLOPE_THRESHOLD가 화자 의존적 ✅ 수정됨 (semitone 기반)
**위치:** `engine.py` line 655  
**내용:** `SLOPE_THRESHOLD = 5.0` Hz/unit 는 절대 Hz 기반. 남성 (100-150Hz 기본 주파수)에서 5Hz 변화는 ~3.5% 이지만, 여성 (200-300Hz)에서는 ~2%. **같은 임계치가 성별에 따라 다르게 작동**  
**개선안:** 세미톤(semitone) 또는 상대 비율 기반으로 변환
```python
# 세미톤 변환
import math
def hz_to_semitone(hz, ref_hz=440.0):
    return 12 * math.log2(hz / ref_hz + 1e-8)
```

---

### MATH-04. `speed_score` 방향 비대칭 미반영 ✅ 수정됨
**위치:** `engine.py` evaluate() line 1148-1153  
**내용:** `100 * (min/max)^k` — 유저가 2배 빠르든 2배 느리든 **동일 감점**. 하지만 학습 관점에서 **너무 빠르게 말하는 것(rushing)**이 느리게 말하는 것보다 더 문제  
**개선안:** 비대칭 페널티 적용
```python
ratio = user_active_time / ref_active_time
if ratio < 1.0:  # 유저가 더 빠름 (rushing)
    speed_score = 100.0 * (ratio ** (k * 1.3))  # 더 강한 감점
else:  # 유저가 더 느림
    speed_score = 100.0 * ((1.0 / ratio) ** k)
```

---

### MATH-05. `rhythm_score` missed 단어 과도한 페널티 ✅ 수정됨 (개선안 A 적용)
**위치:** `engine.py` analyze_word_rhythm() line 904-905  
**내용:** 매칭 실패 단어에 `word_score = 0.0` 부여. 10단어 중 1개만 miss 해도 평균이 크게 하락 (나머지 9개가 완벽해도 최대 90점)  
**문제:** word_accuracy(WER)에서 이미 missed 단어를 감점하므로 **이중 페널티**  
**개선안 A:** missed 단어를 rhythm 평균에서 제외 (WER에만 반영)  
**개선안 B:** 0.0 대신 소량 페널티 (예: 0.3)

---

### MATH-06. `analyze_word_rhythm` k=1.2 하드코딩 ✅ 수정됨
**위치:** `engine.py` line 854  
**내용:** rhythm 민감도 `k = 1.2`가 하드코딩. `config_default.yaml`의 `speed_k: 1.2`와는 별도 값  
**수정:** `config_default.yaml`에 `scoring.rhythm_k` 추가하여 중앙 관리

---

### MATH-07. `analyze_word_rhythm` diff_ratio threshold 0.4 하드코딩 ✅ 수정됨
**위치:** `engine.py` line 889  
**내용:** rushed/dragged 판단 임계치 `0.4`가 하드코딩  
**수정:** config로 관리 (`scoring.rhythm_diff_threshold`)

---

## 3. 모듈 분리 평가

### 현재 구조
```
engine.py — 1286줄 (모든 분석/채점 로직)
main.py   — 583줄 (API + 헬퍼)
config.py — 130줄
constants.py — 73줄
```

### 분리 권장 여부: **현재는 불필요, 조건부 권장**

| 기준 | 현재 | 판단 |
|------|------|------|
| engine.py 줄 수 | ~1286줄 | 관리 가능 범위 |
| 클래스 응집도 | 높음 (모두 평가 파이프라인) | 분리 불필요 |
| 독립 테스트 필요성 | 낮음 (통합 테스트로 충분) | 분리 불필요 |

**향후 분리가 필요한 시점:**
- `engine.py`가 **2000줄 이상**으로 성장할 때
- **Wav2Vec2 음소 분석** (AI-01)이 추가될 때 → `phoneme.py` 분리
- **SM-2 복습 스케줄러** (AI-06)가 추가될 때 → `scheduler.py` 분리

**분리한다면 권장 구조:**
```
pipe/
├── main.py              # API 레이어
├── pipeline.py          # StyleEchoPipeline 클래스 + 싱글턴
├── stt.py               # _extract_whisper_stats, align_text_to_audio
├── scoring.py           # analyze_* 메서드들 (독립 함수화)
├── utils.py             # split_into_sentences, trim_boundary, denoise
├── config.py            # 설정 로더
├── constants.py         # 언어 상수
└── config_default.yaml  # 기본 설정
```

---

## 4. 성능 개선 제안

### PERF-01. FastDTW radius 파라미터 미설정 ✅ 수정됨
**위치:** `engine.py` analyze_prosody() line 934  
**내용:** `fastdtw(ref_features, user_features, dist=euclidean)` — `radius` 미지정 시 전체 탐색  
**개선:** `radius=10` 등으로 제한하면 정확도 손실 없이 **~5배 속도 향상**
```python
distance, path = fastdtw(ref_features, user_features, dist=euclidean, radius=10)
```

---

### PERF-02. `split_into_sentences_with_timestamps` 정규식 반복 컴파일 ✅ 수정됨
**위치:** `engine.py` line 128  
**내용:** `re.compile(r"[^a-zA-Z']")` 이 **매 문장 루프마다** 컴파일됨. 루프 밖 또는 모듈 수준으로 이동  
**수정:** 모듈 수준 상수로 선언

---

### PERF-03. `librosa.load()` 중복 호출 가능성 ✅ 수정됨
**위치:** `engine.py` evaluate() line 1168  
**내용:** `_extract_whisper_stats()`에서 `whisperx.load_audio()` → evaluate()에서 다시 `librosa.load()`. 같은 파일을 2번 로드  
**개선안:** `_extract_whisper_stats()` 가 원본 오디오 배열도 반환하도록 확장, 또는 캐시

---

## 5. 코드 품질 개선 제안

### QUAL-01. Pydantic 응답 모델 미적용 (evaluate-audio) ✅ 수정됨
**위치:** `main.py` line 506  
**내용:** `evaluate_audio()` 엔드포인트에 `response_model` 이 지정되지 않음. `EvaluateAudioResponse` 모델이 정의되어 있으나 실제로 사용하지 않아 응답 검증/문서화 누락  
**수정:** `@app.post("/api/v1/evaluate-audio", response_model=EvaluateAudioResponse)`

---

### QUAL-02. Pydantic 응답 모델 미적용 (generate-reference) ✅ 수정됨
**위치:** `main.py` line 339  
**내용:** `generate_reference()` 도 마찬가지로 `response_model=GenerateReferenceResponse` 미지정  

---

### QUAL-03. `torchaudio` 호환성 패치 중복 ✅ 수정됨
**위치:** `engine.py` line 24-28, `main.py` line 22-25  
**내용:** 동일한 `torchaudio.AudioMetaData` 패치가 **두 파일에 중복**  
**수정:** engine.py 에서만 유지하고 main.py 에서 제거 (engine.py가 먼저 import 됨)

---

## 6. 우선순위 요약

| 우선순위 | 항목 | 영향 | 상태 |
|---------|------|------|------|
| 🔴 높음 | MATH-01 (pause_score 인플레이션) | 총점 7.5점 왜곡 | ✅ 수정됨 |
| 🔴 높음 | MATH-02 (ref_active_time 부정확) | speed + rhythm 왜곡 | ✅ 수정됨 |
| 🟡 중간 | BUG-02 (RemovePunctuation 반복 생성) | 성능 | ✅ 수정됨 |
| 🟡 중간 | BUG-03 (hop_length 불일치) | 잠재적 채점 오류 | ✅ 수정됨 |
| 🟡 중간 | BUG-04 (tmp_dir 미삭제) | 디스크 누수 | ✅ 수정됨 |
| 🟡 중간 | BUG-07 (URL 다운로드 타임아웃) | 프로덕션 안정성 | ✅ 수정됨 |
| 🟡 중간 | MATH-05 (missed 이중 페널티) | 리듬 점수 과도 감점 | ✅ 수정됨 |
| 🟡 중간 | PERF-01 (FastDTW radius) | 성능 (~5배) | ✅ 수정됨 |
| 🔵 낮음 | BUG-01, BUG-05, BUG-06 | 코드 위생 | ✅ 수정됨 |
| 🔵 낮음 | MATH-03, MATH-04, MATH-06, MATH-07 | 채점 정밀도 | ✅ 수정됨 |
| 🔵 낮음 | QUAL-01, QUAL-02, QUAL-03 | 코드 품질 | ✅ 수정됨 |
