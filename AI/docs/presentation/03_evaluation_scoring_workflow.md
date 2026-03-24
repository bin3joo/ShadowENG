# 잉무 AI — 사용자 발화 평가 워크플로우 및 채점 로직

## 전체 흐름 요약

사용자가 레퍼런스 구간을 **따라 읽은 녹음 파일**을 업로드하면,  
AI 서버가 레퍼런스의 프로소디 피처와 비교하여 **7가지 지표**로 채점하고 상세 피드백을 제공합니다.

---

## 상세 워크플로우 (10단계)

### Phase 1: 입력 수신 및 전처리

#### Step 1. API 요청 수신
- 엔드포인트: `POST /api/v1/evaluate-audio`
- 입력:
  - `user_audio`: Base64 인코딩 또는 S3 key / URL
  - `final_script`: 레퍼런스 영어 텍스트
  - `features`: 레퍼런스 F0/RMS 피처 배열
  - `word_timestamps`: 레퍼런스 단어 타임스탬프
  - `hop_length`: 피처 추출에 사용된 hop length

#### Step 2. 사용자 오디오 로드 및 Peak 정규화
- Base64 디코딩 또는 S3/URL 다운로드
- `librosa.load(sr=16000)` → 16kHz 모노로 로드
- **Peak 정규화**: 오디오 최대 진폭을 1.0으로 스케일링 → WhisperX STT 인식률 향상

---

### Phase 2: 사용자 음성 인식 (STT)

#### Step 3. WhisperX STT 실행
- Peak 정규화된 오디오로 `extract_whisper_stats()` 호출
- WhisperX `transcribe()` → `align()` → (선택적) `diarize()`
- 결과: `user_text`, `word_timestamps`, `active_speech_sec`, `pause_count`

#### Step 4. 사용자 단어 정렬 (`_align_user_words_to_ref`)
- 레퍼런스 word_timestamps 구조에 맞춰 사용자 단어를 정렬
- 다중 단어가 하나의 레퍼런스 항목에 대응할 경우 병합

---

### Phase 3: 7대 채점 지표 산출

#### Step 5. 단어 정확도 (Word Accuracy) — 가중치 30%

```
word_score = 100 × exp(-WER_PENALTY × WER)
```

- **WER** (Word Error Rate): `jiwer.wer()` 라이브러리로 산출
- 구두점 제거 후 소문자 비교
- `WER_PENALTY = 2.5` (지수 감쇠 강도)
- WER = 0 → 100점, WER = 0.5 → ~28.7점

#### Step 6. 속도 유사도 (Speed Similarity) — 가중치 7.5%

```
speed_ratio = user_active_time / ref_active_time
```

- **불감대(Deadband)**: ±10% 범위 내면 100점
- 빠를 때(rushing): `100 × (effective)^(k × rushing_penalty)`
  - `k = 1.2`, `rushing_penalty = 1.3`
- 느릴 때: `100 × (1/effective)^k`

#### Step 7. 멈춤 유사도 (Pause Similarity) — 가중치 7.5%

두 가지 점수를 블렌딩합니다:

**1) 횟수 기반 점수 (30%)**
```
count_score = 100 × exp(-(diff²) / (2 × sigma²))
```
- `sigma = 2.5`, `diff = |user_pause_count - ref_pause_count|`

**2) 위치 정합 F1 점수 (70%)**
- 레퍼런스와 사용자의 pause 위치(단어 간 gap ≥ 0.3초)를 비교
- Precision × Recall → F1 score → `align_score = 100 × F1`
- 블렌딩: `pause_score = 0.3 × count_score + 0.7 × align_score`

#### Step 8. 단어 리듬 (Word Rhythm) — 가중치 15%

- 레퍼런스와 사용자의 **단어별 시작·끝 시간** 비교
- 단어 길이 비율과 간격 비율의 편차를 계산
- `rhythm_diff_threshold = 0.4` 이하 차이는 무시
- 각 단어에 `good` / `rushed` / `dragged` / `missed` 피드백 부여

#### Step 9. 억양/강세 분석 (Prosody, Boundary Tone, Dynamic Stress)

**9a. 전체 억양 DTW (Prosody & Stress) — 가중치 20%**
```
prosody_score = 100 × exp(-beta × normalized_DTW_distance)
```
- 레퍼런스·사용자의 `[F0_norm, RMS_norm]` 2차원 벡터를 **Fast DTW**로 비교
- `beta = 1.2`, `DTW_radius = 10`

**9b. 종결 억양 (Boundary Tone) — 가중치 10%**
- 문장 끝부분(마지막 15% 또는 최소 300ms)의 F0 기울기(slope) 비교
- 이동평균(window=3) 평활화 적용
- **같은 방향**: `100 × ((min + bias) / (max + bias))^k`
  - `k = 0.5`, `slope_bias = 0.3`
- **반대 방향**: 기본 40점, 양쪽 모두 평음이면 80점으로 완화
- **Dead zone**: slope 차이가 0.55 이내면 100점

**9c. 역동성 (Dynamic Stress) — 가중치 10%**
- RMS의 상위/하위 분위수 비율(dynamic ratio) 비교
- `dynamic_score = 100 × ((min + 0.1) / (max + 0.1))^1.2`

#### Step 10. 사용자 프로소디 피처 추출
- 사용자 오디오의 발화 구간만 크롭
- 원본 오디오(정규화 전)로 `extract_prosody_features(denoise=True)` 실행
- Track B 디노이징 적용 후 F0/RMS 추출

---

### Phase 4: 종합 점수 산출 및 응답

#### 가중 종합 점수
```
total = word(0.30) + prosody(0.20) + rhythm(0.15) + boundary(0.10) 
      + dynamic(0.10) + speed(0.075) + pause(0.075)
```

#### PASS / FAIL 판정
```
pass_threshold = 60.0
PASS if total_score >= 60.0 else FAIL
```

#### 단어별 피치 컨투어 피드백 (보너스)
- 각 단어 구간의 F0 시작-끝을 비교하여 `rising` / `falling` / `flat` 방향 판정
- 레퍼런스와 사용자를 비교하여 `good` / `rising_expected` / `falling_expected` 피드백

---

## 최종 응답 예시

```json
{
  "status": "SUCCESS",
  "pass_fail": "PASS",
  "pass_threshold": 60.0,
  "user_transcription": "Don't see just a boy...",
  "scores": {
    "total_score": 78.5,
    "word_accuracy": 85.2,
    "prosody_and_stress": 72.1,
    "word_rhythm_score": 68.9,
    "boundary_tone_score": 91.0,
    "dynamic_stress_score": 75.3,
    "speed_similarity": 95.0,
    "pause_similarity": 80.4
  },
  "details": {
    "word_level_feedback": [
      { "word": "Don't", "status": "good", "ref_start_time": 0.12, ... },
      { "word": "see", "status": "rushed", ... }
    ],
    "boundary_tone_feedback": {
      "ref_slope": 0.82, "user_slope": 0.65, "status": "similar"
    },
    "dynamic_stress_feedback": {
      "ref_dynamic_ratio": 3.2, "user_dynamic_ratio": 2.8, "status": "similar"
    },
    "pitch_contour_feedback": [
      { "word": "boy", "ref_direction": "rising", "user_direction": "flat", "feedback": "rising_expected" }
    ]
  }
}
```

---

## 채점 가중치 요약표

| 지표 | 가중치 | 수식 핵심 | 설명 |
|---|---|---|---|
| 단어 정확도 | **30%** | `exp(-2.5 × WER)` | STT 기반 단어 일치율 |
| 억양+강세 | **20%** | `exp(-1.2 × DTW)` | F0+RMS 2D 벡터 DTW |
| 단어 리듬 | **15%** | 단어별 타이밍 비교 | 단어 길이/간격 비율 편차 |
| 종결 억양 | **10%** | F0 꼬리 slope 비교 | 문장 끝 억양 방향 |
| 역동성 | **10%** | RMS dynamic ratio | 강세 변화 정도 |
| 속도 유사도 | **7.5%** | `(min/max)^k` | 전체 발화 속도 |
| 멈춤 유사도 | **7.5%** | count + F1 blend | pause 횟수+위치 |
