# 4. 스코어링 수식 및 논리 (Scoring Logic)

AI 서버 내 `pipeline.py` 가 산출하는 7대 지표 수식과 채점 알고리즘의 세부 수학적/논리적 기작에 대해 설명합니다.

모든 점수는 0점부터 100점 만점 구조이며, 대부분의 로직 구조는 가혹한 선형 차감이 아닌, 인지적 오차를 반영하는 **지수 감쇠(Exponential Decay)** 혹은 **비율의 거듭제곱** 곡선을 채택하고 있습니다.

## 1. 단어 정확도 (Word Accuracy)
*   **지표 설명**: 유저가 레퍼런스에 있는 단어를 틀리지 않고 발화하였는지 확인합니다. 삽입/삭제/대체 오류를 종합합니다.
*   **수식**: 
    1.  양측 문장의 구두점을 모두 제거 후 소문자로 변환합니다.
    2.  `jiwer` 패키지를 통해 WER (Word Error Rate; 단어 오류율)을 도출합니다.
    3.  `score = 100.0 * exp(-k * WER)` (상수 `k=config.WER_PENALTY`, default 2.5)
*   **특징**: 에러율이 조금 오를 때 점수가 부드럽게 깎기며, 절반 이상 틀리면 0점에 폭넓게 수렴하는 지수 함수 형태입니다.

## 2. 속도 유사도 (Speed)
*   **지표 설명**: 실제 말을 입 밖으로 내뱉은 유효 발화 시간(`active_speech_sec`)의 속도가 일치하는지 봅니다.
*   **수식**: `ratio = 유효 발화 시간(user) / 유효 발화 시간(ref)` (상수 `k=1.2`, `penalty=1.3`)
    *   느릴 경우 (`ratio > 1`): `score = 100.0 * (1.0 / ratio) ** k`
    *   빠를 경우 (`ratio < 1`): `score = 100.0 * ratio ** (k * penalty)`
*   **특징**: 기준치 대비 너무 빠르게 랩을 하듯이 말하는 것을 더 가혹하게 감점(`rushing_penalty`)하도록 설계되었습니다.

## 3. 멈춤 유사도 (Pause)
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
*   **지표 설명**: 개별 단어 길이를 초 단위 절대 비교를 하는 게 아니라, 전체 문장에서 해당 단어가 차지하는 **비중(Relative Duration; RD)**을 계산합니다.
*   **수식**: 각 단어 단위 스코어를 구한 후 산술 평균을 냅니다 (`k=1.2`).
    `ref_RD = ref_duration / ref_active_time`
    `user_RD = user_duration / user_active_time`
    `word_score = 100.0 * (min(ref_RD, user_RD) / max(ref_RD, user_RD)) ** k`
*   **특징**: 이 과정에서 한 쪽이 40% 이상(RHYTHM_DIFF_THRESHOLD) 오차가 날 경우 너무 끌었는지(`dragged`), 급하게 삼켰는지(`rushed`) 피드백을 출력합니다. 

## 5. 억양 전체 유사도 (Prosody)
*   **지표 설명**: 시간 축이 다르더라도 전체 음의 높낮이 흐름 구조가 유사하면 높은 점수를 주는 알고리즘입니다.
*   **수식**: DTW (Dynamic Time Warping; 동적 시간 왜곡 알고리즘) 적용. `beta=1.2`
    1.  각 발동의 F0(기본 주파수) 곡선을 화자 기본 음고 오프셋 값에 대해 정규화.
    2.  각 발동의 RMS(볼륨 에너지) 곡선은 표준 정규화(Z-score) 변환. (`[f0, rms]` 스택 벡터 형성)
    3.  `distance = DTW_Euclidean(ref, user) / path_len`
    4.  `score = 100.0 * exp(-beta * distance)`
*   **특징**: 화자의 기본 성별이 다르더라도(높은 톤 vs 낮은 톤), 곡선 트렌드가 유사하게 오르고 내리면 거리 패널티가 발생하지 않습니다. 시간 축을 고무줄처럼 묶어 융통성 있게 거리를 잽니다.

## 6. 종결 억양 (Boundary Tone)
*   **지표 설명**: 영어 문장 끝을 말아 올리는지, 평음으로 내리는지에 대한 특징 여부 판단입니다.
*   **수식**: 문장이 끝나기 직전 발화의 **마지막 15% 구간** F0 배열 데이터를 가져옵니다.
    1.  F0의 물리 단위를 음악의 세미톤 단위(`12 * log2(hz / 55.0)`)로 변환합니다. 변동 폭을 균일화하기 위함입니다.
    2.  시간 축 0~1(x)에 대한 세미톤 배열값(y)을 넣고 1차 다항식 회귀(`numpy.polyfit`)를 돌려 **Slope (기울기)**를 구합니다.
    3.  레퍼런스와 유저의 기울기 **부호**가 일치하고 크기가 유사하면 `score = 100.0 * (min / max) ** k` (상수 `k=config.BOUNDARY_K`, default 0.8) 점수를 부여합니다. 서로 방향이 다르면(`r_m * u_m < 0`) 가혹한 페널티(Opposite, 40점)를 부여합니다.
    4.  최종 점수가 `config.BOUNDARY_GOOD_THRESHOLD` (default 80.0) 이상이거나 둘 다 평음(Threshold 미만 파형)이면 피드백은 Good 입니다.

## 7. 역동성 (Dynamic Stress)
*   **지표 설명**: 로봇처럼 일정하게 책을 읽는지, 원어민처럼 단어마다 강세에 악센트를 주면서 크게 말했다 작게 말했다를 적절히 조절하는지 판단합니다.
*   **수식**: 통계학의 변동 계수(Coefficient of Variation, CV) 값 산출.
    1.  해당 유성음 구간 내의 볼륨 파형(RMS)에 대한 평균(mean)과 표준편차(std)를 구합니다.
    2.  `cv_ratio = std / mean`
    3.  `score = 100.0 * (min(r_cv, u_cv) / max(r_cv, u_cv)) ** k` (상수 `k=config.DYNAMIC_K`, default 1.2)
*   **특징**: 볼륨의 역동성이 낮을수록 로봇 같은 발화에 가깝습니다. 점수가 `config.DYNAMIC_GOOD_THRESHOLD` (default 80.0) 이상이면 Good으로 판정합니다.

## 8. 단어 피치 컨투어 (Word Pitch Contour)
*   **지표 설명**: 각 단어별 억양 단위(F0 주파수 변화량)를 검사하여 구체적인 피드백(예: 올려치기, 내려치기 등)을 산출합니다.
*   **특징**: 단어의 처음과 끝 주파수(Hz) 차이가 `config.PITCH_FLAT_THRESHOLD_HZ` (default 5.0 Hz) 미만이라면 유의미한 억양 변화가 없는 평음(`flat`)으로 판정합니다.
