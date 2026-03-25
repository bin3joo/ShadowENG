# 4. 스코어링 수식 및 논리 (Scoring Logic)

`domain/scoring/` 패키지 내 세분화된 채점 엔진들이 산출하는 **7대 지표**의 수식과 알고리즘의 세부 수학적·논리적 기작에 대해 설명합니다.

모든 점수는 0점부터 100점 만점 구조이며, 대부분의 로직 구조는 가혹한 선형 차감이 아닌, 인지적 오차를 반영하는 **지수 감쇠(Exponential Decay)** 혹은 **비율의 거듭제곱** 곡선을 채택하고 있습니다.

> **핵심 변경점 (2026-03-25)**: 억양 유사도(Prosody) 수식이 단순 `exp(-β × DTW_distance)` 에서, **Pearson 상관계수 기반 유사도 + DTW Timing Penalty의 Hybrid 모드**로 전면 교체되었습니다. 이로 인해 곡선의 "모양 정합"을 더욱 정밀하게 측정합니다.

## 1. 단어 정확도 (Word Accuracy)
*   **구현 모듈**: `pipeline.py`
*   **지표 설명**: 유저가 레퍼런스에 있는 단어를 틀리지 않고 발화하였는지 확인합니다. 삽입/삭제/대체 오류를 종합합니다.
*   **수식**: 
    1.  양측 문장의 구두점을 모두 제거 후 소문자로 변환합니다.
    2.  `jiwer` 패키지를 통해 WER (Word Error Rate; 단어 오류율)을 도출합니다.
    3.  `score = 100.0 * exp(-k * WER)` (상수 `k=config.WER_PENALTY`, default 2.5)
*   **특징**: 에러율이 조금 오를 때 점수가 부드럽게 깎기며, 절반 이상 틀리면 0점에 폭넓게 수렴하는 지수 함수 형태입니다.

## 2. 속도 유사도 (Speed)
*   **구현 모듈**: `pipeline.py`
*   **지표 설명**: 실제 말을 입 밖으로 내뱉은 유효 발화 시간(`active_speech_sec`)의 속도가 일치하는지 봅니다.
*   **수식**: `ratio = 유효 발화 시간(user) / 유효 발화 시간(ref)` (상수 `k=config.SPEED_K`, `penalty=config.SPEED_RUSHING_PENALTY`)
    *   **불감대(Deadband)**: `|ratio − 1.0| ≤ config.SPEED_DEADBAND` (default ±10%) 이내이면 **100점** (미세한 속도 차이는 무시)
    *   느릴 경우 (`ratio > 1 + deadband`): `score = 100.0 * (1.0 / effective_ratio) ** k`
    *   빠를 경우 (`ratio < 1 - deadband`): `score = 100.0 * effective_ratio ** (k * penalty)`
*   **특징**: 기준치 대비 ±10% 이내의 자연스러운 속도 변동은 감점하지 않습니다. 그 범위를 초과할 때부터 거듭제곱 감점이 시작됩니다.

## 3. 멈춤 유사도 (Pause)
*   **구현 모듈**: `domain/scoring/pause_scoring.py`
*   **지표 설명**: '몇 번 쉬었는가(횟수)'뿐 아니라, '**어디서 쉬었는가(위치)**'까지 평가합니다. 횟수 기반 점수와 위치 정합(F1) 점수를 블렌딩합니다.
*   **수식**: 2단계 블렌딩 (`w = config.PAUSE_ALIGN_WEIGHT`, default 0.7)

    **A. 횟수 기반 점수 (Count Score)**: 가우시안 분포 (σ = `config.PAUSE_SIGMA`)
    `diff = abs(user_pause_count - ref_pause_count)`
    `count_score = 100.0 * exp(-(diff²) / (2 * σ²))`

    **B. 위치 정합 점수 (Alignment Score)**: Precision-Recall F1
    1.  레퍼런스/유저 양쪽의 단어 간 gap > `config.PAUSE_ALIGN_GAP_SEC` 인 **위치(단어 인덱스)**를 집합(Set)으로 추출합니다.
    2.  `true_hits = |ref ∩ user|`, `false_alarms = |user − ref|`, `misses = |ref − user|`
    3.  `precision = true_hits / (true_hits + false_alarms)`
    4.  `recall = true_hits / (true_hits + misses)`
    5.  `f1 = 2 × precision × recall / (precision + recall)`
    6.  `align_score = 100.0 * f1`

    **C. 최종 블렌딩**:
    `pause_score = (1 − w) × count_score + w × align_score`

*   **특징**: 횟수만 동일해도 엉뚱한 위치에서 끊어 읽으면 위치 F1이 낮아져 감점됩니다. 반대로 정확히 의미 단위에서 쉬면 만점에 접근합니다. 위치 비교가 불안정한 경우(단어 대응이 부족할 때) 횟수 기반이 안전망(Fallback) 역할을 합니다.

## 4. 단어 리듬 점수 (Rhythm)
*   **구현 모듈**: `domain/scoring/rhythm_scoring.py`
*   **지표 설명**: 개별 단어 길이를 초 단위 절대 비교를 하는 게 아니라, 전체 문장에서 해당 단어가 차지하는 **비중(Relative Duration; RD)**을 계산합니다.
*   **수식**: 각 단어 단위 스코어를 구한 후 산술 평균을 냅니다 (`k=1.2`).
    `ref_RD = ref_duration / ref_active_time`
    `user_RD = user_duration / user_active_time`
    `word_score = 100.0 * (min(ref_RD, user_RD) / max(ref_RD, user_RD)) ** k`
*   **특징**: 이 과정에서 한 쪽이 40% 이상(RHYTHM_DIFF_THRESHOLD) 오차가 날 경우 너무 끌었는지(`dragged`), 급하게 삼켰는지(`rushed`) 피드백을 출력합니다. 

## 5. 억양 전체 유사도 (Prosody) — Hybrid Mode ⭐

*   **구현 모듈**: `domain/scoring/prosody_scoring.py`
*   **지표 설명**: 시간 축이 다르더라도 전체 음의 높낮이 흐름 구조가 유사하면 높은 점수를 주는 알고리즘입니다. 노이즈나 침묵 구간이 채점을 방해하지 않도록 정교한 전처리를 거칩니다.

### 5-A. 전처리 파이프라인 (Feature Extraction)
1.  **Smart Cropping**: 시작 단어부터 끝 단어까지의 실제 발화 구간만 잘라내어 앞뒤의 무음이 평균을 깎아먹는 현상을 차단합니다. (`domain/scoring/aggregator.py`)
2.  **F0 전처리 및 기준선 0 고정**: `librosa.pyin`의 `voiced_flag` 정보를 바탕으로 무성음/침묵 구간을 확실히 `0.0`으로 마스킹하고, 중간의 튀는 값들을 **메디안 필터(kernel=5)**로 부드럽게 깎아냅니다. (`domain/prosody/feature_extraction.py`)
3.  **RMS 기준선 0 고정**: RMS(볼륨) 역시 단순 Z-Score를 넘어서, 하위 15% 이하의 극소 에너지는 무음으로 간주하여 `0.0`으로 강제 고정합니다.
4.  정규화된 `[f0_norm, rms_norm]` 스택 벡터를 Fast DTW로 정렬합니다 (`radius=config.PROSODY_DTW_RADIUS`, default 10).

### 5-B. Scoring Mode 분기 (`config.PROSODY_SCORING_MODE`)

현재 기본값은 **`hybrid`** 모드이며, 3가지 모드를 지원합니다:

**Mode 1: `distance` (순수 DTW 거리 기반)**
```
distance_score = 100.0 × exp(-β × normalized_DTW_distance)
```
- `β = config.PROSODY_BETA` (default 1.2)
- DTW 경로 길이로 나눈 정규화 거리를 지수 감쇠합니다.

**Mode 2: `similarity` (순수 유사도 기반)**
```
f0_sim  = Pearson_Correlation(ref_f0_aligned, user_f0_aligned)
rms_sim = Pearson_Correlation(ref_rms_aligned, user_rms_aligned)
similarity_score = 100.0 × (f0_sim × f0_weight + rms_sim × rms_weight)
```
- `f0_weight = config.PROSODY_F0_WEIGHT` (default 0.7)
- `rms_weight = config.PROSODY_RMS_WEIGHT` (default 0.3)
- 유사도 메트릭은 `config.PROSODY_SIMILARITY_METRIC`으로 `pearson`(기본) 또는 `cosine` 중 선택 가능합니다.

**Mode 3: `hybrid` (기본값 ⭐ — 유사도 × 타이밍 보정)**
```
timing_ratio = config.PROSODY_TIMING_PENALTY_WEIGHT  (default 0.25)
timing_penalty = (1.0 − timing_ratio) + timing_ratio × (distance_score / 100.0)
prosody_score = similarity_score × timing_penalty
```
- **핵심 직관**: 피치/볼륨의 "모양"이 일치하는지(Pearson)를 메인 점수로 삼되, DTW 거리가 크면(타이밍이 많이 어긋나면) `timing_penalty`가 0.75~1.0 범위에서 벌점을 가합니다.
- 예) 유사도 90점이지만 DTW 거리가 커서 distance_score=40점이면 → `timing_penalty = 0.75 + 0.25 × 0.4 = 0.85` → 최종 `90 × 0.85 = 76.5점`

*   **특징**: 무음 구간의 높이가 양쪽 모두 `0.0`으로 완벽히 일치하게 되어, DTW 알고리즘이 침묵 때문에 시간 축을 억지로 비틀며 발생하는 감점 요소가 완벽히 사라졌습니다. 화자의 기본 톤이 달라도 곡선 트렌드만 일치하면 점수를 획득합니다.

## 6. 종결 억양 (Boundary Tone)
*   **구현 모듈**: `domain/scoring/boundary_scoring.py`
*   **지표 설명**: 영어 문장 끝을 말아 올리는지, 평음으로 내리는지에 대한 특징 여부 판단입니다.
*   **수식**: 문장이 끝나기 직전 발화의 **마지막 15% 구간 또는 최소 `config.BOUNDARY_TAIL_MIN_MS`ms (default 300ms)** 중 긴 쪽의 F0 배열 데이터를 가져옵니다.
    1.  F0에 이동평균(window=3) 평활화를 적용하여 노이즈 왜곡을 방지합니다.
    2.  F0의 물리 단위를 음악의 세미톤 단위(`12 * log2(hz / 55.0)`)로 변환합니다.
    3.  시간 축 0~1(x)에 대한 세미톤 배열값(y)을 넣고 1차 다항식 회귀(`numpy.polyfit`)를 돌려 **Slope (기울기)**를 구합니다.
    4.  같은 방향: **Soft Ratio** — `score = 100.0 * ((min + bias) / (max + bias)) ** k` (상수 `k=config.BOUNDARY_K` default 0.5, `bias=config.BOUNDARY_SLOPE_BIAS` default 0.3). bias가 평음 근처에서 점수 폭락을 방지합니다.
    5.  반대 방향: 양쪽 모두 평음에 가까우면(`SLOPE_THRESHOLD * 2` 미만) `config.BOUNDARY_OPPOSITE_SOFT_SCORE` (default 80점), 한쪽이 뚜렷하면 `config.BOUNDARY_OPPOSITE_SCORE` (default 40점).
    6.  양쪽 모두 Dead Zone (`config.BOUNDARY_SLOPE_THRESHOLD` 미만)이면 100점.

## 7. 역동성 (Dynamic Stress)
*   **구현 모듈**: `domain/scoring/dynamic_scoring.py`
*   **지표 설명**: 로봇처럼 일정하게 책을 읽는지, 원어민처럼 단어마다 강세에 악센트를 주면서 크게 말했다 작게 말했다를 적절히 조절하는지 판단합니다.
*   **수식**: 통계학의 변동 계수(Coefficient of Variation, CV) 값 산출.
    1.  해당 유성음 구간 내의 볼륨 파형(RMS)에 대한 평균(mean)과 표준편차(std)를 구합니다.
    2.  `cv_ratio = std / mean`
    3.  `score = 100.0 * (min(r_cv, u_cv) / max(r_cv, u_cv)) ** k` (상수 `k=config.DYNAMIC_K`, default 1.2)
*   **특징**: 볼륨의 역동성이 낮을수록 로봇 같은 발화에 가깝습니다. 점수가 `config.DYNAMIC_GOOD_THRESHOLD` (default 80.0) 이상이면 Good으로 판정합니다.

## 8. 단어 피치 컨투어 (Word Pitch Contour)
*   **구현 모듈**: `domain/scoring/pitch_contour.py`
*   **지표 설명**: 각 단어별 억양 단위(F0 주파수 변화량)를 검사하여 구체적인 피드백(예: 올려치기, 내려치기 등)을 산출합니다.
*   **특징**: 단어 구간의 피치 차이(diff)를 평균 F0로 나눈 **비율(%)** 이 `config.PITCH_FLAT_THRESHOLD_RATIO` (default 4%) 미만이면 평음(`flat`)으로 판정합니다. Hz 절대값 대신 비율을 사용하여 남성/여성/아동 등 화자의 기본 피치 대역에 관계없이 일관된 피드백을 줍니다.
