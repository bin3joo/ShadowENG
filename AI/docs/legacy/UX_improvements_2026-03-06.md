# StyleEcho UX 개선 현황

> 최종 업데이트: 2026-03-06  
> 기준: `pipe/engine.py` + `pipe/main.py` + `pipe/config.py` + `pipe/constants.py` 현재 구현  
> **code_review_2026-03-05.md 전 항목 수정 반영됨**

---

## 범례
- ✅ **적용됨** — 코드에 구현 완료
- ~~취소선~~ **삭제됨** — 폐기된 항목
- 🔵 **미적용** — 프론트엔드 또는 향후 구현 대상

---

## 1. 채점 로직 수정 현황

| 항목 | 이전 | 현재 | 상태 |
|------|------|------|------|
| `word_score` 수식 | `100 - WER*100` — WER=100%일 때 0점 | `100 * exp(-2.5*WER)` — 최소 ~8점 보장, 0점 미만 불가 | ✅ **개선됨** |
| `diff_ratio` 대칭화 | `abs(r-u)/(r+ε)` — 비대칭 | `abs(r-u)/((r+u)/2+ε)` | ✅ **적용됨** |
| `rhythm_score` 분모 | `len(ref_words)` — 구두점 포함 오류 | `len(word_scores)` — 실제 평가 단어 수 | ✅ **적용됨** |
| `pause_score` sigma | `1.5` — VAD 불안정성으로 과도한 감점 | `config.PAUSE_SIGMA` (기본 2.5) | ✅ **적용됨 + config 연동** |
| `pause_score` 계산 | ref_data에 pause 정보 없음 → 항상 100점 | ref word gap 기반 `_count_pauses_from_words()` 실측 계산 | ✅ **수정됨** |
| `speed_score` 대칭 | 빠르든 느리든 동일 감점 | 비대칭 페널티 (`speed_rushing_penalty`) | ✅ **수정됨** |
| `rhythm_score` missed 단어 | 0점 부여 → 이중 페널티 | missed 단어 평균에서 제외 (WER에만 반영) | ✅ **수정됨** |
| `ref_active_time` | `last_end - first_start` (pause 포함) | `_sum_word_durations()` (word duration 합산) | ✅ **수정됨** |
| `boundary_tone` dead zone | 없음 — 평탄한 ref에서 0점 오탐 | semitone 기반 slope + `config.BOUNDARY_SLOPE_THRESHOLD` (0.35) | ✅ **개선됨** |
| `dynamic_stress` status | CV ratio 독립 판별 — score와 불일치 | `score≥80` → `"good"` 보장 | ✅ **적용됨** |
| `evaluate()` denoise | 레퍼런스+유저 모두 raw | 유저만 `denoise=True` (Track B) | ✅ **적용됨** |
| 모든 하이퍼파라미터 | 하드코딩 | `config_default.yaml` 에서 중앙 관리 | ✅ **적용됨** |
| `evaluate()` 시그니처 | `(ref_audio, user_audio, ref_text)` | `(user_audio, ref_data)` — ref 오디오 불필요 | ✅ **리팩터링됨** |
| ~~`_evaluate_with_features()`~~ | main.py 별도 함수 | engine.py `evaluate()`로 통합, 함수 삭제 | ✅ **통합됨** |
| `pause_score` | ref 오디오 기반 계산 | ref word gap 기반 가우시안 계산 | ✅ **수정됨** |
| 언어 상수 분리 | engine.py 인라인 (~70줄) | `constants.py` 별도 파일로 분리 | ✅ **적용됨** |

### 스코어링 수식 통일성 (모두 0점 미만 불가)

| 지표 | 수식 형태 | 최솟값 보장 |
|------|-----------|------------|
| word_accuracy | `100 * exp(-2.5*WER)` | ~8점 (WER=100%) |
| prosody | `100 * exp(-β*dist)` | >0 |
| speed | `100 * ratio^(k*rushing)` / `100 * (1/ratio)^k` | 0 |
| pause | `100 * exp(-diff²/2σ²)` | >0 |
| rhythm | `100 * mean(ratios)` | 0 |
| boundary_tone | 규칙 기반 | 40 (방향 반대) |
| dynamic_stress | `100 * (min/max)^1.2` | 0 |

---

## 2. 피드백 표현 현황

| 항목 | 상태 | 비고 |
|------|------|------|
| `word_level_feedback` (rushed/dragged/missed) | ✅ 구현 | engine.py `analyze_word_rhythm` |
| `pitch_contour_feedback` (단어별 피치 방향 비교) | ✅ **신규 구현** | raise_end / lower_end / more_emphasis / good |
| 한국어 번역 (`sentence_ko`, `final_script_ko`) | ✅ **신규 구현** | Helsinki-NLP/opus-mt-en-ko |
| `extra` 단어 피드백 | 🔵 미적용 | 프론트에서 처리 가능 |
| 한국어 피드백 메시지 | 🔵 미적용 | 프론트 매핑 테이블로 처리 |
| 총점 등급 레이블 (S/A/B/C/D) | 🔵 미적용 | 프론트에서 처리 가능 |
| 연음/축약 패턴 감지 | ✅ 구현 | `parts[].reductions` 30종 패턴 |
| 문장별 핵심 표현 | ✅ 구현 | `parts[].key_expressions` |

### 피치 컨투어 피드백 상세

`pitch_contour_feedback` (evaluate-audio 응답의 details 내)

```json
{
  "word": "meeting",
  "ref_direction": "falling",
  "ref_start_hz": 220.5,
  "ref_end_hz": 180.3,
  "user_direction": "rising",
  "user_start_hz": 195.2,
  "user_end_hz": 240.1,
  "feedback": "lower_end"
}
```

| feedback 값 | UX 메시지 (프론트 매핑 예시) |
|-------------|---------------------------|
| `good` | ✅ 좋아요! |
| `raise_end` | ↗ 끝을 올려서 발음하세요 |
| `lower_end` | ↘ 끝을 내려서 발음하세요 |
| `more_emphasis` | 💪 더 강하게 억양을 넣으세요 |

---

## 3. 게임 메카닉

| 항목 | 상태 | 비고 |
|------|------|------|
| ~~콤보 시스템 (Perfect/Good/Retry + combo_add)~~ | ~~삭제됨~~ | 완전 제거 |
| ~~`evaluate-chunk` 엔드포인트~~ | ~~삭제됨~~ | 의도적 삭제 |
| ~~`_to_chunk_judgement` 헬퍼~~ | ~~삭제됨~~ | evaluate-chunk 와 함께 제거 |

---

## 4. API 응답 구조

| 항목 | 상태 | 비고 |
|------|------|------|
| `generate-reference` → `parts[]` (파트별 데이터) | ✅ 구현 | sentence, features, word_timestamps, difficulty |
| `generate-reference` → `sentence_ko` 한국어 번역 | ✅ **신규 구현** | 파트별 + 전체 스크립트 |
| `generate-reference` → `trimmed_word_count` | ✅ 구현 | 경계 정제로 제거된 단어 수 |
| `evaluate-audio` JSON body (S3 URL/base64) | ✅ **적용됨** | 기존 multipart form → JSON body |
| `evaluate-audio` → `pitch_contour_feedback` | ✅ **적용됨** | 단어별 피치 높낮이 피드백 |
| `evaluate-audio` → `pipeline.evaluate()` 직접 호출 | ✅ **리팩터링됨** | main.py는 API 레이어만 담당 |
| Pydantic 모델 전면 정비 | ✅ **적용됨** | `PartData`, `EvaluateAudioRequest`, `PitchContourFeedback` 등 |

---

## 5. 에러 및 예외 UX

| 항목 | 상태 |
|------|------|
| 무음 감지 (`FAIL` + 메시지 반환) | ✅ 구현 (`user_text == ""` 분기) |
| 커스텀 예외 클래스 | 🔵 미적용 |
| 저볼륨 RMS 임계치 선제 안내 | 🔵 미적용 |

---

## 6. 성능 및 처리 UX

| 항목 | 상태 | 비고 |
|------|------|------|
| Two-Track 디노이징 (noisereduce STG) | ✅ 구현 | Track A (STT 원본) / Track B (librosa 디노이즈) |
| 구간 경계 정제 (`trim_boundary_fragments`) | ✅ 구현 | config 연동 |
| 문장 단위 파싱 + 다중 단어 타임스탬프 처리 | ✅ 구현 | "Hollywood casting" 같은 뭉친 항목 지원 |
| 연음 통일 (`_align_user_words_to_ref`) | ✅ **신규 구현** | 유저 단어를 ref 구조에 1:1 매칭/병합 |
| 문장별 난이도 자동 분류 | ✅ 구현 | Easy/Normal/Hard/Expert |
| YouTube 캡션 Fast Path | ✅ 구현 | caption_align: STT 건너뛰기, ~10x 빠름 |
| YAML 기반 설정 관리 | ✅ **신규 구현** | `config_default.yaml` → `config.yaml` 딥 머지 |
| 한국어 번역 모델 지연 로드 | ✅ **적용됨** | 첫 호출 시 다운로드, 이후 캐시 |
| `constants.py` 언어 상수 분리 | ✅ **신규 구현** | REDUCTION_PATTERNS, A1_WORDS |
| 레퍼런스 피처 DB 캐싱 | 🔵 미적용 | Java 서버 DB 연동 필요 |
| 처리 단계 스트리밍 (SSE/WebSocket) | 🔵 미적용 | 장기 목표 |

---

## 7. 하이퍼파라미터 현황

> 모든 파라미터가 `config_default.yaml` 에서 중앙 관리됨. `config.yaml` 오버라이드 가능.

| 파라미터 | 기본값 | YAML 키 | 비고 |
|---------|--------|---------|------|
| Whisper 모델 | `large-v3` | `whisper.model` | tiny~large-v3 |
| target_sr | `16000` | `audio.target_sr` | — |
| hop_length | `256` | `audio.hop_length` | F0/RMS 해상도 |
| prop_decrease | `0.8` | `denoise.prop_decrease` | 1.0=최대 제거(왜곡 위험) |
| caption_padding | `3.0초` | `padding.caption_sec` | 자막 패딩 |
| audio_padding | `2.0초` | `padding.audio_sec` | 오디오 패딩 |
| front_score_threshold | `0.6` | `trimming.front_score_threshold` | — |
| back_score_threshold | `0.45` | `trimming.back_score_threshold` | — |
| boundary_gap_sec | `0.2초` | `trimming.boundary_gap_sec` | — |
| min_words | `2` | `trimming.min_words` | — |
| speed_k | `1.2` | `scoring.speed_k` | 1.0(선형)~1.5(엄격) |
| **speed_rushing_penalty** | **`1.3`** | **`scoring.speed_rushing_penalty`** | **신규** — rushing 비대칭 페널티 |
| pause_sigma | `2.5` | `scoring.pause_sigma` | — |
| **pause_gap_sec** | **`0.3`** | **`scoring.pause_gap_sec`** | **신규** — 단어 간 gap 임계치 |
| prosody_beta | `1.2` | `scoring.prosody_beta` | DTW 감쇠 |
| **prosody_dtw_radius** | **`10`** | **`scoring.prosody_dtw_radius`** | **신규** — FastDTW 반경 |
| **rhythm_k** | **`1.2`** | **`scoring.rhythm_k`** | **신규** — 리듬 민감도 |
| **rhythm_diff_threshold** | **`0.4`** | **`scoring.rhythm_diff_threshold`** | **신규** — rushed/dragged 임계치 |
| **boundary_slope_threshold** | **`0.35`** | **`scoring.boundary_slope_threshold`** | **신규** — semitone dead zone |
| 가중치 (7개) | — | `scoring.weights.*` | 합 = 1.0 |
