# 잉무 AI — 레퍼런스 생성 워크플로우

## 전체 흐름 요약

사용자가 YouTube 영상의 `video_id`와 `start_sec ~ end_sec` 구간을 지정하면,  
AI 서버가 해당 구간의 **원어민 발화를 분석**하여 학습용 레퍼런스 데이터를 생성합니다.

---

## 상세 워크플로우 (12단계)

### Phase 1: 입력 수신 및 병렬 준비

#### Step 1. API 요청 수신
- 엔드포인트: `POST /api/v1/generate-reference`
- 입력: `{ video_id, start_sec, end_sec }`
- Pydantic 스키마(`GenerateReferenceRequest`)로 video_id 형식(11자리), 시간 범위 유효성 검증

#### Step 2. 병렬 다운로드 (ThreadPoolExecutor, max_workers=4)
동시에 3가지 작업을 병렬로 시작합니다:

| 작업 | 구현 | 설명 |
|---|---|---|
| **자막 조회** | `fetch_youtube_captions()` | YouTube Transcript API로 영어 수동 자막 조회. 자동 자막은 접두어 fallback으로만 사용 |
| **오디오 다운로드** | `download_reference_audio()` | yt-dlp Python API로 구간 오디오를 WAV로 다운로드 (앞뒤 padding 2초 포함) |
| **보컬 분리 (VR)** | `separate_vocals()` | audio-separator(htdemucs_ft)로 보컬만 추출 (config로 활성화/비활성화) |

---

### Phase 2: 음성 인식 및 타임스탬프 생성

#### Step 3. 경로 분기 — Fast Path vs Full Path

```
자막 있음? (manual caption)
  ├─ Yes → Fast Path: Caption Alignment (STT 생략, ~10배 빠름)
  └─ No  → Full Path: WhisperX STT + Forced Alignment
```

**Fast Path (Caption Alignment):**
1. 패딩된 자막 텍스트를 단일 세그먼트로 포장
2. WhisperX `align()`이 텍스트를 오디오 파형에 강제 매핑
3. Ghost Word 필터링 (4단계):
   - timestamp 없는 단어 제거 (오디오에서 찾지 못함)
   - `score < 0.1` 단어 제거 (억지 매칭)
4. Caption 건강도 평가(`evaluate_caption_alignment_health`):
   - `surviving_word_ratio` 확인 (55% 미만이면 fallback 고려)
   - 앞부분 low-confidence 비율, leading/trailing gap 확인
   - 복합 신호가 2개 이상이면 → Full Path로 fallback

**Full Path (Whisper STT):**
1. WhisperX `transcribe()`: 음성 → 텍스트 (large-v3, batch_size=16)
2. WhisperX `align()`: 텍스트 → 단어별 타임스탬프
3. pyannote Diarization: 화자 라벨 부여 (선택적)

#### Step 4. 구간 리베이스
- 다운로드 패딩(2초)을 감안하여 단어 타임스탬프를 요청 구간 `[start_sec, end_sec]` 기준으로 리베이스
- 요청 구간 밖의 단어는 제거

---

### Phase 3: 텍스트 정제 및 경계 트리밍

#### Step 5. 경계 단어 트리밍 (`trim_boundary_fragments`)
- **앞부분**: alignment score < 0.65인 저신뢰 단어를 제거
- **뒷부분**: alignment score < 0.38이면서 오디오 끝에 0.15초 이내인 단어를 제거
- 최소 2개 단어는 유지

#### Step 6. 텍스트 정제 (`sanitize_reference_text`)
- 특수 문자 제거 (영문, 숫자, 기본 구두점만 유지)
- 다중 공백 정규화
- 구두점 앞 공백 제거

---

### Phase 4: 프로소디 피처 추출

#### Step 7. 오디오 품질 추정 및 디노이즈 모드 선택
- `estimate_reference_audio_metrics()`: speech_ratio, SNR(dB), noise_level 계산
- `select_reference_denoise_mode()`: 노이즈 레벨에 따라 `off` / `mild` / `moderate` 자동 선택

#### Step 8. F0/RMS 프로소디 피처 추출
- 원본 오디오에서 `extract_prosody_features()` 실행:
  - `librosa.pyin()`: C2~C7 대역 F0 추출 (hop_length=256)
  - `librosa.feature.rms()`: 에너지 추출
  - F0 화자 정규화 (유성음 기준 Z-score)
  - RMS Z-score 정규화

#### Step 9. VR Gating — 소스 선택 (보컬 분리 활성화 시)
- VR 오디오에서도 별도로 F0/RMS를 추출
- `select_reference_prosody_sources()`: 4가지 품질 지표로 F0/RMS 각각에 대해 원본 vs VR 중 더 나은 소스를 선택
  - F0 선택: `voiced_ratio` / `jump_ratio` 기반
  - RMS 선택: `contrast_db` / `dropout_ratio` 기반

---

### Phase 5: 문장 분할 및 파트 구성

#### Step 10. 문장/턴 분할 (`split_into_sentences_with_timestamps`)

1. **문장 분할**: `.?!` 뒤 공백 기준으로 분리
2. **턴 분할** (대화형 영상): pause gap, 화자 전환, 구두점, 최대 단어 수를 고려한 turn 경계 생성
3. **분할 방식 자동 선택**: 화자가 2명 이상이거나 턴 수가 더 많으면 → 턴 분할 사용
4. **짧은 파트 병합** (`merge_short_reference_parts`): 1초 미만의 짧은 파편을 인접 파트와 병합 (화자 호환성 확인)

각 파트에 부여되는 메타데이터:
- `sentence`, `start_sec`, `end_sec`, `duration_sec`
- `difficulty` (Easy/Normal/Hard/Expert), `difficulty_score`
- `key_expressions` (A1 제외 핵심 어휘)
- `word_timestamps`, `pause_count`
- `features` (파트별 F0/RMS 피처 배열)

---

### Phase 6: 번역 및 학습 콘텐츠 생성

#### Step 11. Gemini LLM 호출 (`translate_reference_parts_with_gemini`)

Gemini에 파트 데이터를 전송하여 3가지 작업을 동시 수행:

1. **전체 텍스트 한국어 번역** — 자연스러운 구어체(영화 자막 수준) 번역
2. **파트 병합 및 파트별 번역** — 인접 파트 중 논리적으로 연속된 것을 최대 15초 제한으로 병합, 각 병합 파트에 `sentence_ko` 부여
3. **어휘 및 학습 표현 추출** — 파트별 3~5개 어휘 + 전체 3~5개 학습 표현 (발음, 뉘앙스, 예문 포함)

- 최대 3회 재시도 (429, timeout, MAX_TOKENS 등 오류 시 지수 백오프)
- Pydantic 기반 응답 파싱 및 검증 (`source_part_ids` 순서 전수 검사)

---

### Phase 7: 품질 판정 및 응답 생성

#### Step 12. 품질 게이트 판정 (`assess_reference_quality`)

| 검사 항목 | reject 조건 | risky 조건 |
|---|---|---|
| 정렬 신뢰도 | low_alignment_ratio ≥ 60% | low_alignment_confidence |
| 겹침 비율 | overlap_ratio ≥ 42% | medium_overlap (26%~42%) |
| 노이즈 | — | high_noise / medium_noise |
| 화자 | — | multi_speaker_detected |
| 대화 양상 | — | dialog_like |

- `reject` → HTTP 422 에러 응답 (레퍼런스 부적합)
- `risky` → 경고 포함 정상 응답 (주의해서 사용)
- `good` → 정상 응답

#### 최종 응답 페이로드 구성
```json
{
  "status": "SUCCESS",
  "video_id": "...",
  "final_script": "...",
  "final_script_ko": "...",
  "parts": [
    {
      "sentence": "...", "sentence_ko": "...",
      "start_sec": 0.0, "end_sec": 5.0,
      "difficulty": "Easy",
      "word_timestamps": [...],
      "features": { "f0_array": [...], "rms_array": [...] },
      "vocabulary": [...]
    }
  ],
  "learning_expressions": [...],
  "reference_quality": "good",
  "hop_length": 256
}
```
