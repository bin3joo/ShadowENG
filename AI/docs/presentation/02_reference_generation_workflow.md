# 잉무 AI — 레퍼런스 생성 워크플로우

## 전체 흐름 요약

사용자가 YouTube 영상의 `video_id`와 `start_sec ~ end_sec` 구간을 지정하면,  
AI 서버가 해당 구간의 **원어민 발화를 분석**하여 학습용 레퍼런스 데이터를 생성합니다.

이번 업데이트를 통해 **LLM(Gemini) 번역 프로세스와 오디오 피처(Prosody) 추출 로직을 완벽히 병렬화**하여, 서버의 전체 응답 속도를 혁신적으로 단축했습니다.

---

## 상세 워크플로우 (12단계)

### Phase 1: 입력 수신 및 병렬 준비

#### Step 1. API 요청 수신
- 엔드포인트: `POST /api/v1/generate-reference`
- 입력: `{ video_id, start_sec, end_sec }`
- Pydantic 스키마(`GenerateReferenceRequest`)로 video_id 형식(11자리) 및 시간 범위 유효성 검증

#### Step 2. 병렬 다운로드 (ThreadPoolExecutor, max_workers=4)
동시에 3가지 작업을 병렬로 시작합니다:

| 작업 | 구현 | 설명 |
|---|---|---|
| **자막 조회** | `fetch_youtube_captions()` | `youtube_caption_enabled`가 `true`(기본값)일 때만 실행. YouTube Transcript API로 영어 수동 자막 조회. `false`이면 자막 조회 단계를 건너뛰고 항상 Whisper STT Full Path로 진행 |
| **오디오 다운로드** | `download_reference_audio()` | yt-dlp Python API로 구간 오디오를 WAV로 다운로드 (앞뒤 padding 2초 포함) |
| **보컬 분리 (VR)** | `separate_vocals()` | audio-separator(htdemucs_ft)로 여음/BGM 분리 (config 로 활성/비활성화) |

---

### Phase 2: 음성 인식 및 원본 텍스트 정제

#### Step 3. 경로 분기 — Fast Path vs Full Path (STT)
```text
자막 조회 활성화? (youtube_caption_enabled)
  ├─ false → 무조건 Full Path: WhisperX STT + Forced Alignment
  └─ true  →
       자막 있음? (manual caption)
         ├─ Yes → Fast Path: Caption Alignment (STT 생략 처리속도 극대화)
         └─ No  → Full Path: WhisperX STT + Forced Alignment
```
- STT 및 단어별 타임스탬프(`word_timestamps`) 정렬은 **항상 원본 오디오**를 기준으로 수행되어, 배경 노이즈 환경에서도 사용자가 듣게 될 실제 음원과 동일한 기준을 유지합니다.

#### Step 4. 텍스트 정제 및 구간 리베이스
- 다운로드 패딩(2초)을 감안하여 단어 타임스탬프를 요청 구간 `[start_sec, end_sec]` 기준으로 리베이스
- 신뢰도(Confidence)가 낮은 양극단 경계 단어 보수적 트리밍(`trim_boundary_fragments`)
- 특수문자 제거 및 텍스트 띄어쓰기 정제(`sanitize_reference_text`)

---

### Phase 3: 문장 분할 및 LLM 병렬 트리거 🚀 (핵심 최적화 구간)

#### Step 5. 문장 / 턴 분할 (`split_into_sentences_with_timestamps`)
- 정제된 최종 스크립트와 타임스탬프 배열을 기반으로 **즉시 문장을 분할**합니다.
- `.?!` 뒤 공백을 기준으로 1차 분할 후, 턴 분할(대화형 로직) 혹은 짧은 파편 병합을 통해 기본적인 `sentence_data` 뼈대를 만듭니다.

#### Step 6. Gemini LLM 번역 즉각 실행 (Background Submit)
- 문장 뼈대가 만들어지자마자, 즉시 ThreadPoolExecutor를 통해 `translate_reference_parts_with_gemini`를 백그라운드로 던집니다.
- **포인트**: LLM이 네트워크를 타며 번역(전체 번역, 파트 병합, 학습 어휘 추출)을 고민하는 수 초 동안, 서버는 놀지 않고 곧바로 다음의 무거운 음성 추출(Phase 4) 연산에 돌입합니다.

---

### Phase 4: 프로소디 피처 추출 (LLM 연산과 동시 진행)

#### Step 7. 오디오 품질 추정 및 디노이즈 모드 선택
- `estimate_reference_audio_metrics()`: 오디오의 SNR(dB), 노이즈 레벨 측정
- 측정된 노이즈 수준에 따라 Prosody 디노이즈 강도를 `off` / `mild` / `moderate` 3단계로 자동 선택합니다.

#### Step 8. F0/RMS 프로소디 추출 (Original vs VR 병렬)
- `extract_prosody_features`: 원음(Original)과 분리음(VR) 양쪽에서 각각 기본 주파수(F0)와 볼륨(RMS) 에너지를 병렬로 추출합니다.
- 추출 과정 중, Pyin 30Hz 미만 대역을 0으로 깎는 하드 마스킹 및 `Smart Cropping`, 메디안 필터링(Kernel=5)이 가해집니다.

#### Step 9. VR Gating — 최적 소스 채택
- `select_reference_prosody_sources()` 알고리즘을 통해, 추출된 두 버전 중 더 깔끔한 Pitch와 RMS를 각 항목별로 크로스 게이팅(채택)합니다.

---

### Phase 5: 품질 판정 및 최종 융합

#### Step 10. 파트 분석 부착 및 Reference 품질 게이트 평가
- Step 9에서 확정된 최종 프로소디 데이터와 Speaker 정보를 Step 5의 `sentence_data` 뼈대에 덧붙입니다.
- **품질 게이트 판정**: `low_alignment_ratio`(정렬 불량율)가 60% 이상이거나, 화자가 심하게 겹치면(`overlap_ratio`) 422 Reject를 발생시킵니다.
- Reject 시, 뒤에서 돌아가고 있는 Gemini Future 객체에 `cancel()` 신호를 날려 불필요한 비용 낭비를 차단합니다.

#### Step 11. LLM 번역 결과 회수 (Join) 및 데이터 재병합
- `translation_future.result()`를 대기하여 번역 결과물(한국어 스크립트, 파트별 번역, 주요 어휘 2~4종, 학습 표현 2~4개)을 받아옵니다.
- Gemini가 파트를 합쳤을(Merge) 가능성을 대비해, 프로소디와 타임스탬프를 다시 번역된 병합 구조에 맞게 조립합니다. (병합 후 총 지속시간 최대 **10초** 제한)

#### Step 12. 최종 응답 페이로드 조립
- 종합된 `parts` 배열과 오디오 통계, `reference_quality` 지표를 탑재한 JSON 응답을 만들어 클라이언트에게 전송(HTTP 200)하고 안전하게 임시 파일을 파기합니다.

---

## 품질 및 가중치 응답 구조

```json
{
  "status": "SUCCESS",
  "video_id": "NrO20Jb-hy0",
  "final_script": "...",
  "final_script_ko": "자, 오늘 우리는...",
  "parts": [
    {
      "sentence": "...", 
      "sentence_ko": "...",
      "start_sec": 0.0, 
      "end_sec": 5.0,
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
