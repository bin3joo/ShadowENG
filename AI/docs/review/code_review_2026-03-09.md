# StyleEcho 코드 리뷰

> 날짜: 2026-03-09  
> 대상: `pipe/` 전체  
> 기준: `video_id` 계약 전환, 단순 분리 구조 적용, AI 서버 `pass_fail` 반환 정책 반영 후 코드 기준

---

## 0. 요약

이번 작업에서는 정책 변경 사항을 실제 코드와 문서에 반영했습니다.

주요 반영 사항은 다음과 같습니다.

- `generate-reference` 입력을 `youtube_url` 에서 `video_id` 로 전환
- AI 서버 평가 응답에 `pass_fail`, `pass_threshold` 추가
- `scores.total_score` 는 그대로 유지하여 백엔드 재판정 가능 구조 유지
- `main.py` 의 역할 일부를 단순 분리 구조로 이동
- `schemas.py`, `youtube_service.py`, `io_utils.py`, `reference_service.py` 신설
- `generate-reference` top-level 응답에 `pause_count`, `active_speech_sec`, `word_count` 추가
- `evaluate-audio` 의 `s3://` 문서/구현 불일치 해소
- 잘못된 base64 입력이 500이 아닌 400으로 처리되도록 보완

현재 상태는 **기능적으로 한 단계 정리된 상태**이며, 이전보다 API 계약과 코드 책임이 더 명확해졌습니다.

---

## 1. 이번 작업에서 적용된 변경 사항

### CHG-01. `generate-reference` 입력 계약을 `video_id` 로 전환

**적용 결과:** 완료

- 요청 모델이 `youtube_url` 대신 `video_id` 를 받도록 변경됨
- `youtube_service.py` 에서 canonical YouTube URL 을 재조립함
- `yt-dlp` 와 caption fetch 모두 `video_id` 중심으로 처리됨
- 응답에는 `video_id`, `youtube_url` 을 함께 포함하여 추적성과 디버깅 편의를 유지함

**영향 파일**
- `pipe/schemas.py`
- `pipe/youtube_service.py`
- `pipe/reference_service.py`
- `pipe/main.py`
- `pipe/api-spec.md`

---

### CHG-02. AI 서버 평가 응답에 `pass_fail` 반환 추가

**적용 결과:** 완료

- `engine.py` 에서 `total_score` 계산 후 임시 기준점 `60.0` 으로 `PASS`/`FAIL` 판정 수행
- 응답에 아래 필드 추가
  - `pass_fail`
  - `pass_threshold`
- 기존 `scores.total_score` 는 그대로 유지됨

**설계 평가**
- AI 서버 판정값을 그대로 사용할 수 있음
- 동시에 백엔드는 `scores.total_score` 로 언제든지 자체 재판정 가능
- 정책과 점수 데이터를 함께 제공하는 구조로 확장성 확보

**영향 파일**
- `pipe/engine.py`
- `pipe/config.py`
- `pipe/config_default.yaml`
- `pipe/schemas.py`
- `pipe/api-spec.md`

---

### CHG-03. 단순화된 1차 분리 구조 적용

**적용 결과:** 완료

기존 `main.py` 내부 책임 일부를 아래와 같이 분리했습니다.

- `schemas.py`
  - 요청/응답 Pydantic 모델
  - 기본 입력 검증
- `youtube_service.py`
  - canonical URL 생성
  - `yt-dlp` 명령 구성/다운로드
  - caption fetch
- `io_utils.py`
  - 임시 파일/디렉토리 삭제
  - remote audio download
- `reference_service.py`
  - sentence part 후처리
  - top-level reference 응답 조립
  - 번역 orchestration 일부 정리

**평가**
- 과도한 폴더 분리 없이도 `main.py` 부담이 줄어듦
- 이후 폴더 구조 확장 시에도 자연스럽게 이전 가능

---

### CHG-04. `generate-reference` top-level 메타 확장

**적용 결과:** 완료

응답 최상위에 아래 필드가 추가되었습니다.

- `pause_count`
- `active_speech_sec`
- `word_count`

**효과**
- 백엔드가 part 외 section 단위 메타를 바로 활용 가능
- 캐시/통계/확장 평가에 재계산 비용 감소

---

## 2. 이번 점검에서 발견되었고 수정된 오류

### FIX-01. `s3://` 문서/구현 불일치

**문제**
- 문서 또는 설명상 `s3://` 지원처럼 보였으나 실제 구현은 `http/https` 만 허용

**조치**
- `evaluate-audio` 경로를 `http/https` 또는 base64 로 명확히 정리
- API 명세도 동일하게 수정

**상태:** 수정 완료

---

### FIX-02. invalid base64 입력이 서버 오류(500)로 전파될 수 있던 문제

**문제**
- base64 디코딩 실패가 일반 예외로 흘러 500이 될 수 있었음

**조치**
- `binascii.Error` 를 명시적으로 처리하여 400 `HTTPException` 으로 변환

**상태:** 수정 완료

---

### FIX-03. private/public 경계 일부 정리

**문제**
- 외부 레이어에서 내부용 메서드/함수를 호출하는 구조가 일부 존재했음

**조치**
- pause 카운트 유틸을 public 함수 `count_pauses_from_words` 로 승격
- Whisper 통계 추출도 `extract_whisper_stats` public 메서드로 정리

**상태:** 수정 완료

---

### FIX-04. mutable default 사용 정리

**문제**
- Pydantic 모델의 리스트 필드에서 mutable default 직접 사용 패턴이 존재했음

**조치**
- `Field(default_factory=list)` 로 전환

**상태:** 수정 완료

---

## 3. 현재 남아있는 개선 포인트

### IMP-01. 예외 계층화는 아직 제한적임

현재도 `main.py`, `engine.py` 일부 경로는 `except Exception` 을 사용합니다.

**영향**
- 입력 오류 / 외부 도구 오류 / 내부 로직 오류의 분류가 충분히 세분화되지 않음

**권장 방향**
- `CaptionFetchError`
- `ReferenceDownloadError`
- `TranslationError`
- `EvaluationInputError`
같은 도메인 예외를 도입하고 API 레이어에서 변환

---

### IMP-02. `pass_fail` 와 `status` 의 의미를 소비자에게 명확히 안내할 필요가 있음

현재 응답에는 아래 두 축이 존재합니다.

- `status`
  - 요청 처리 성공/실패
- `pass_fail`
  - 채점 결과 합격/불합격

**주의점**
- `status == "SUCCESS"` 이면서 `pass_fail == "FAIL"` 은 정상적인 케이스임
- 백엔드/프론트에서 두 필드를 혼동하지 않도록 명확한 계약 유지 필요

---

### IMP-03. reference 번역은 여전히 파트별 순차 호출 구조임

`translate_reference_parts()` 는 전체 스크립트 + 각 sentence 번역을 순차 수행합니다.

**영향**
- 문장 수가 많을수록 응답 지연 증가 가능

**권장 방향**
- translation cache
- 비동기/배치 처리
- 필요 시 번역 비활성 옵션

---

### IMP-04. `main.py` 는 여전히 orchestration 책임이 남아 있음

분리는 진행됐지만 `main.py` 는 아직 아래를 직접 조정합니다.

- Fast/Slow path 선택
- trim 실행
- librosa 로드
- feature 추출 orchestration
- service 결과 결합

**평가**
- 이전보다는 개선됐으나, 장기적으로는 endpoint → service 호출만 남기는 구조가 더 좋음

---

## 4. 파일별 검토 메모

### `main.py`

- 역할은 줄었지만 아직 orchestration 중심 파일
- base64 오류 처리 개선 완료
- `video_id` 기준 계약 반영 완료

### `engine.py`

- 채점 핵심 로직은 일관성 있게 유지됨
- `pass_fail` 반환 정책 추가 완료
- public 메서드 경계 일부 정리 완료

### `schemas.py`

- 입력 검증 강화됨
- `video_id`, 시간 범위, audio format, word score 범위 검증 포함

### `youtube_service.py`

- YouTube 관련 책임이 한곳에 모여 유지보수성 향상
- canonical URL 재조립 전략이 명확해짐

### `reference_service.py`

- part 후처리와 top-level 응답 조립이 분리됨
- 향후 sentence-level 확장 포인트로 적절함

### `api-spec.md`

- 실제 구현과 거의 동기화됨
- `video_id`, `pass_fail`, `pass_threshold`, top-level pause 메타 반영 완료

---

## 5. 권장 다음 작업

1. `main.py` 의 generate/evaluate orchestration 을 각각 service 함수로 한 단계 더 분리
2. 예외 계층화 및 구조화된 로깅 키(`video_id`, `stt_method`, fallback reason) 도입
3. 번역 latency 최적화 여부 검토
4. 필요 시 `caption_status`, `reference_source` 같은 관측성 필드 추가
5. 실제 요청 샘플 기준 smoke test 수행

---

## 6. 결론

2026-03-09 기준으로 이번 정책 반영 작업은 핵심 요구사항을 충족했습니다.

- `video_id` 계약 전환 적용됨
- AI 서버 `pass_fail` 반환 적용됨
- 점수 병행 반환 구조 유지됨
- 단순 분리 구조 적용됨
- 문서 동기화 진행됨

남은 과제는 주로 **운영 관측성**, **예외 분류**, **추가 구조 분리** 수준이며, 현재 상태는 이전보다 명확하고 확장 가능한 형태입니다.

---

## 7. 현재 적용된 레퍼런스 품질 처리 구조

이 섹션은 최근 추가된 `generate-reference` 품질 판단, 화자분리, 대화형
분할, 노이즈 처리 로직을 현재 코드 기준으로 정리합니다.

### AP-01. 단일 화자 고품질 레퍼런스 선별

**적용 상태:** 완료

현재 파이프라인은 단일 화자, 정렬 신뢰도가 높고, 배경 노이즈가 낮은
레퍼런스를 우선적으로 좋은 샘플로 취급합니다.

핵심 판단 신호는 아래와 같습니다.

- `alignment_median_score`
- `low_alignment_ratio`
- `estimated_snr_db`
- `noise_level`
- `overlap_risk_ratio`
- `speaker_shift_ratio`
- `speaker_mode`

단, 이 중 `speaker_shift_ratio` 는 **현재 구현된 보조 휴리스틱**일 뿐,
화자 동일성의 강한 증거로 간주하면 안 됩니다.

**적용 이유**

- 쉐도잉/억양 학습용 레퍼런스는 발화 내용보다도 **한 명의 안정된 음성**이
  중요함
- 강한 노이즈, 겹침, 화자 교체가 있으면 prosody feature와 word timing이
  오염됨
- 후단 점수 비교에서 reference 자체가 흔들리면 user score 해석도 어려워짐

**수학적 근거**

- 정렬 품질:
  - `low_alignment_ratio = (# low confidence words) / (# total words)`
  - low-confidence 단어 비율이 높을수록 forced alignment 신뢰도 저하로 해석
- 겹침 위험:
  - 인접 단어 gap 중 `gap <= 0.02` 인 비율을 `overlap_risk_ratio` 로 사용
  - 음절/단어가 연속적으로 비정상 겹침을 보이면 멀티스피커 혹은 타이밍 오염
    가능성이 높음
- 화자 불안정:
  - part별 유성구간 `median F0` 를 비교하고
  - `shift = |12 * log2(curr / prev)|` 로 semitone 차이를 계산
  - 일정 semitone 이상 급변이 반복되면 화자 교체 가능성을 **약하게 시사**할 수 있음

**재검토 결과**

- `speaker_shift_ratio` 는 수학적으로 계산 자체는 문제 없지만,
  **F0는 화자 식별자라기보다 발성 상태와 감정 변화에 민감한 값**입니다.
- 따라서 현재 방식은 `speaker instability` 를 강하게 주장하는 근거로는 부족합니다.
- 특히 설명형 영상, 감정 변화가 큰 독백, 강조 발화에서는 false positive 위험이 큽니다.

즉 현재 F0 기반 shift는 아래 용도에만 제한적으로 적합합니다.

- `speaker_mode` 의 보조 경고 신호
- diarization 부재 시 fallback heuristic
- 후처리 검토용 observability metric

반대로 아래 용도로는 부적합합니다.

- 단일 화자 여부의 1차 판정 근거
- reject 또는 강한 multi-speaker 판정의 핵심 근거

**권장 수정 방향**

- 우선순위 1: diarization `return_embeddings=True` 활용 검토
- 우선순위 2: part 간 speaker embedding cosine distance 기반 유사도 측정
- 우선순위 3: F0 shift는 보조 feature 로만 유지

**현재 한계**

- diarization이 없는 경우는 여전히 prosody heuristic 의존 비율이 높음
- 단일 화자 감정 변화도 speaker instability 로 잡힐 수 있음
- 실제 화자 동일성 판단은 embedding 계열 feature 가 훨씬 타당함

**추가 적용됨 (2026-03-09)**

- word-level diarization label 을 part 단위 token 비율로 재집계
- `detected_speaker_count >= 2` 만으로 즉시 multi-speaker 로 확정하지 않음
- 아래 단일 화자 지지 조건을 만족하면 `single_speaker_assumed` 유지
  - `dominant_speaker_word_ratio`
  - `second_speaker_word_ratio`
  - `speaker_label_change_ratio`
  - `multi_speaker_part_ratio`
  - `dominant_speaker_part_ratio`
- 반대로 두 번째 화자 비중과 label change / multi-part 비율이 함께 높을 때만
  `multi_speaker_suspected` 로 승격

즉 현재는 **"2명 라벨 감지" → 즉시 multi-speaker** 구조가 아니라,
**지배 화자 우세 + 전환 안정성 + 혼합 part 비율**을 함께 보는 gating 구조로 바뀌었습니다.

---

### AP-02. 환경 노이즈 처리 및 adaptive denoise

**적용 상태:** 완료

현재 구조는 **STT용 원본 오디오**와 **prosody 분석용 오디오**를 분리하는
Two-Track 방식을 사용합니다.

- WhisperX STT / align 에는 원본 오디오 사용
- F0 / RMS 분석에는 필요 시 denoise 적용

**적용 이유**

- STT는 과도한 denoise 시 자음 소실, 포먼트 왜곡으로 오히려 정렬 품질이
  나빠질 수 있음
- 반면 F0/RMS 같은 저수준 feature는 moderate denoise 로 더 안정될 수 있음

**현재 추정 지표**

- `speech_mask`: 단어 타임스탬프 구간을 speech 로 마킹
- `speech_rms`
- `noise_rms`
- `estimated_snr_db = 20 * log10(speech_rms / noise_rms)`
- `speech_ratio = mean(speech_mask)`

**수학적 근거**

- RMS는 에너지의 제곱평균제곱근으로 음성/배경 에너지의 상대 크기를 반영
- SNR를 dB 스케일로 변환해 사람이 느끼는 에너지 차이를 더 선형적으로 다룸
- `speech_ratio` 가 낮으면 실제 발화보다 무음/배경음 비율이 높다고 해석 가능

**재검토 결과**

- `estimated_snr_db = 20 * log10(speech_rms / noise_rms)` 자체는 올바른 형태입니다.
- 하지만 현재 구현은 `word_timestamps` 로만 speech mask 를 만들기 때문에,
  단어 경계 밖의 무성 자음, 숨소리, release burst 가 `noise_samples` 로 새어
  들어갈 수 있습니다.
- 이 경우 `noise_rms` 가 실제보다 커지고 SNR 이 과소평가될 수 있습니다.

또한 현재 코드에는 아래 fallback 이 있습니다.

```python
if noise_rms <= 1e-8:
    noise_rms = max(1e-8, speech_rms * 0.15)
```

이 분기에서는 최대 측정 가능 SNR 이 사실상 아래 값 근처로 제한됩니다.

```text
20 * log10(1 / 0.15) ≈ 16.48 dB
```

즉 극히 조용한 오디오도 이 분기를 타면 고품질 SNR 이 충분히 분리되지 못하는
상한 효과가 생길 수 있습니다.

**adaptive denoise 정책**

- `noise_level == high` → `moderate`
- `noise_level == medium` → `mild`
- `noise_level == low` → `off`

**현재 한계**

- speech mask 가 word timestamp 기반이라 VAD-only 구간은 반영되지 않음
- 비정상 impulsive noise 는 RMS 기반 단순 추정보다 더 정교한 특징이 필요함
- 무성 자음/호흡 구간 누수로 noise_rms 가 과대평가될 수 있음
- `speech_rms * 0.15` fallback 은 매우 조용한 환경의 SNR 분해능을 낮출 수 있음

**권장 수정 방향**

- word mask 앞뒤에 `0.1s ~ 0.2s` safety margin 을 두어 dilation 적용
- 가능하면 VAD 기반 speech region 과 word timestamp 를 함께 사용
- `speech_rms * 0.15` 대신 고정 `noise_floor` 상수 또는 percentile 기반 하한선 검토
- 현재 `estimated_snr_db` 는 정밀 계측치가 아니라 운영용 근사치로 문서화

---

### AP-03. 짧은 문장 병합

**적용 상태:** 완료

`REFERENCE_MIN_PART_DURATION_SEC` 미만 part 는 인접 part 와 병합합니다.

**적용 이유**

- 1초 미만 part 는 F0/RMS 길이가 매우 짧아 안정적 비교가 어려움
- 지나치게 짧은 part 는 UI/학습용 phrase 로도 활용성이 낮음
- diarization 또는 punctuation 오류로 잘게 쪼개진 문장을 완화할 수 있음

**수학적 근거**

- 샘플 수가 너무 적으면 median F0, RMS variance, WPM 추정치가 불안정해짐
- 병합 시 인접 gap 을 기준으로 가장 가까운 part 에 합쳐 분산을 줄임

---

### AP-04. 실제 diarization 연동

**적용 상태:** 완료

현재는 WhisperX wrapper 를 통해 `pyannote/speaker-diarization-3.1` 을 사용해
word-level speaker label 을 부여합니다.

추가된 메타데이터는 아래와 같습니다.

- top-level
  - `diarization_used`
  - `detected_speaker_count`
- part-level
  - `dominant_speaker`
  - `speaker_count`
  - `speaker_risk`
- word-level
  - `speaker`

**적용 이유**

- heuristic speaker risk 만으로는 실제 멀티스피커 구간과 단일 화자 감정 변화를
  분리하기 어려움
- 대화형 clip 에서는 word-level speaker label 이 있어야 turn 분할 품질을 올릴 수 있음

**운영 최적화**

- diarization 은 lazy load
- 기본 device 는 `reference.diarization_device = cpu`
- 서버 시작 시 preload 하지 않음

이 구조는 GPU OOM 위험을 줄이면서 필요 시에만 diarization 을 사용하기 위한
선택입니다.

---

## 8. 다중 화자 멀티턴 대화 처리 현황

### 8.1 현재 적용된 프로세스

**적용 상태:** 완료

현재 멀티턴 대화 처리 흐름은 아래와 같습니다.

1. WhisperX STT 또는 caption-align 로 word timestamp 생성
2. 가능하면 diarization 으로 word speaker label 부여
3. diarization label 에 대해 짧은 끼어듦 구간 smoothing 수행
4. pause, punctuation, speaker change, minimum evidence 를 함께 사용해
   `turn` 분리 수행
5. 짧은 part 병합 시 dominant speaker 가 다른 경계는 보존
6. part별 dominant speaker 와 speaker count 계산
7. 품질 평가 단계에서 `multi_speaker_detected`, `speaker_mode`,
   `dialog_mode` 를 결정

### 8.2 현재 turn 분리 기준

**적용 상태:** 완료

`split_into_dialog_turns()` 는 현재 아래 조건을 사용합니다.

- terminal punctuation
- `next_gap >= REFERENCE_TURN_GAP_SEC`
- 안정적인 `speaker_change`
- `min_turn_words`, `min_turn_duration_sec` 기반 minimum evidence
- 단어 수가 충분히 많고 gap 이 절반 이상일 때 chunk 분리
- clip 종료

즉 현재는 **pause 기반 분할만 쓰는 구조가 아니라**, speaker-aware signal 을
minimum evidence 와 함께 gating 하는 구조로 확장되었습니다.

### 8.3 현재 한계

- backchannel / short reply 전용 탐지는 아직 없음
- overlap boundary 에 대한 세밀한 모델링이 아직 없음
- embedding 기반 post-merge 는 아직 없음

---

## 9. 다중 화자 멀티턴 인식률 향상을 위한 추가 프로세스

이 섹션은 **미적용 / 후속 후보** 위주로 정리합니다.

### IMP-MT-01. speaker-aware boundary score 도입

**적용 상태:** 미적용

초기에는 pause 기반 분리를 선형 결합 점수로 확장하는 아이디어를 검토했습니다.

예를 들어 아래 신호를 합산하는 방식입니다.

- `pause_gap_score`
- `speaker_change_score`
- `overlap_penalty`
- `short_reply_bonus`

하지만 재검토 결과, **이 순수 선형 결합 방식은 현재 단계에서 권장하지 않습니다.**

이유는 아래와 같습니다.

- diarization label 은 짧은 구간에서 과분할될 수 있음
- `speaker_change_score` 가 noisy 하다면 문장 중간 false boundary 가 급증할 수 있음
- 각 신호의 신뢰도가 다르므로 단순 가중합은 오류를 쉽게 전파함

즉 문제는 점수 조합 자체보다도, **입력 신호의 신뢰도 보장과 gating 부재**에 있습니다.

현재 더 적합한 방향은 아래와 같습니다.

- 1차: diarization over-segmentation 완화
- 2차: minimum evidence 기반 gating
- 3차: 그 이후에만 score 기반 ranking 또는 confidence 계산

권장되는 boundary 확정 규칙 예시는 아래와 같습니다.

```text
if pause_gap >= min_pause_sec
and speaker_change is stable
and new_speaker_support >= min_support
and boundary_overlap <= max_overlap_sec:
    accept_turn_boundary
else:
    keep_same_turn
```

즉 현재 추천안은 **weighted sum** 이 아니라 **decision-tree 형태의 비선형 gating** 입니다.

### IMP-MT-02. speaker purity 지표 추가

**적용 상태:** 부분 적용

turn 후보 내부에 대해 아래 지표를 계산할 수 있습니다.

- `dominant_speaker_ratio`
- `speaker_switch_count`
- `speaker_entropy`

해석 예시:

- dominant speaker 비율이 높으면 한 화자 turn 가능성 높음
- 한 turn 안 speaker switch 가 여러 번 나오면 과하게 묶였을 가능성 높음

현재 코드에는 아래 일부가 이미 반영되어 있습니다.

- `dominant_speaker_word_ratio`
- `second_speaker_word_ratio`
- `dominant_speaker_part_ratio`
- `multi_speaker_part_ratio`

### IMP-MT-03. minimum evidence 규칙 추가

**적용 상태:** 적용됨

화자 전환이 감지되더라도 아래 조건을 만족하지 않으면 turn 분리를 보류합니다.

- `min_turn_words`
- `min_turn_duration_sec`
- `min_speaker_support_ratio`

이 규칙은 diarization 과분할로 인한 false boundary 를 줄이는 데 유효합니다.

이 항목은 현재 다중 화자 turn segmentation 개선에서
`IMP-MT-01` 보다 더 선행 우선순위로 보는 것이 타당합니다.

### IMP-MT-04. backchannel / short reply 탐지

**적용 상태:** 미적용

아래 특성을 가진 응답은 독립 turn 후보로 취급할 수 있습니다.

- 단어 수가 매우 적음
- 새로운 speaker 에 의해 발화됨
- 길이는 짧지만 독립 응답 의미를 가짐

예:

- `yeah`
- `okay`
- `I'm listening`
- `right`

### IMP-MT-05. diarization 후처리 merge

**적용 상태:** 부분 적용

동일 화자 과분할 완화를 위해 아래 후처리를 고려할 수 있습니다.

- 인접 speaker embedding 유사도 기반 merge
- 인접 turn 의 median F0 / RMS / WPM 유사도 기반 merge
- 지나치게 짧고 dominant speaker 가 동일한 turn 재병합

현재 코드에는 아래가 이미 적용되어 있습니다.

- 짧은 끼어듦 diarization label smoothing
- dominant speaker 가 다른 part 사이 병합 방지

재검토 결과, 이 항목은 단순 boundary scoring 보다 **우선순위가 더 높습니다.**

이유는 아래와 같습니다.

- over-segmentation 이 남아 있으면 boundary 모델이 아무리 좋아도 입력이 흔들림
- 잘못 분리된 speaker label 을 먼저 smoothing 해야 downstream turn split 이 안정됨
- embedding 기반 merge 는 F0 기반 merge 보다 화자 동일성 관점에서 더 타당함

---

## 10. 현재 코드에서 바로 적용 가능한 추가 하이퍼파라미터

현재 WhisperX diarization wrapper 수준에서 바로 제어 가능한 값은 아래와 같습니다.

- `num_speakers`
- `min_speakers`
- `max_speakers`
- `return_embeddings`

### 왜 중요한가

- 동일 화자 과분할의 가장 직접적인 완화 수단은 `max_speakers`
- 화자 수를 거의 아는 구간에서는 `num_speakers` 가 가장 강한 제약
- `return_embeddings=True` 는 후처리 merge 에 필요한 기반 정보 제공 가능

### 권장 방향

- 기본값: `min_speakers=1`, `max_speakers=2`
- 명백한 대화형 clip: `max_speakers=2` 또는 `3`
- 독백형 후보: `max_speakers=1`

이 방식은 현재 wrapper 를 크게 바꾸지 않고도 과분할을 줄일 수 있는 현실적인 선택입니다.

---

## 11. 다른 주요 영역 정리

### OTH-01. caption source 정책

현재는 manual caption 이 있으면 우선 사용하고, auto caption 만 있을 경우는
caption-align 대신 `whisper_stt` 로 폴백합니다.

**이유**

- auto caption 은 word timing 과 텍스트 품질이 불안정한 경우가 많음
- 강제 정렬 입력으로 사용하면 boundary fragment 와 ghost word 가 늘어날 수 있음

### OTH-02. boundary fragment trim

clip 앞뒤 단어에 대해 아래 조건을 함께 봅니다.

- boundary 와의 거리
- alignment score
- 문장 끝 구두점 존재 여부

이는 **잘린 문장 조각**이 최종 reference 에 포함되는 것을 줄이기 위한 규칙 기반 정제입니다.

### OTH-03. 응답 관측성 강화

최근 응답에는 아래 메타데이터가 포함됩니다.

- `reference_quality`
- `quality_reasons`
- `warnings`
- `denoise_mode`
- `speaker_mode`
- `dialog_mode`
- `caption_source`
- `alignment_median_score`
- `estimated_snr_db`
- `noise_level`
- `diarization_used`
- `detected_speaker_count`

이 정보는 운영 중 threshold 튜닝과 사례 분석의 기반이 됩니다.

---

## 12. 권장 다음 작업

1. `return_embeddings=True` 기반 post-merge 전략 실험
2. `dominant_speaker_ratio`, `speaker_switch_count`,
   `boundary_overlap_sec` 를 응답 메타로 추가
3. backchannel / short reply 전용 탐지 규칙 추가
4. 고품질 단일 화자 / 대화형 / noisy clip 별 regression sample 세트 고정
5. reference 생성 / evaluate-audio Whisper 모델 분리 운영은 **보류**

---

## 13. 최종 정리

현재 코드베이스는 단순한 sentence split 수준을 넘어 아래 단계를 이미 갖추고 있습니다.

- reference quality 선별
- adaptive denoise
- boundary trim
- 짧은 part 병합
- 실제 diarization 연동
- part 단위 speaker 메타데이터 생성

다만 멀티화자 멀티턴 대화에 대해서는 아직 **pause 중심 분할**의 성격이 강하므로,
다음 단계는 **speaker-aware turn segmentation** 과 **과분할 merge 후처리**입니다.

즉, 현재는 `품질 판별 + speaker 탐지` 까지는 들어왔고, 다음 목표는
`speaker 정보를 실제 turn 분할 규칙으로 끌어오는 것` 입니다.

---

## 7. engine.py 모듈 분리 리팩토링

### 7.1 분석 요약

**문제:** `engine.py` 가 89KB / 2,379줄로 비대하며, 6개 이상의 독립 책임 영역이 하나의 파일에 혼재.

| 영역 | 줄 수 (약) | 함수/클래스 수 | SRP 위반 여부 |
|---|---|---|---|
| 공유 유틸리티 | ~60 | 4 | — |
| 문장/턴/파트 분할·병합 | ~400 | 11 | ✅ |
| 화자 분석 | ~200 | 7 | ✅ |
| 품질 평가 | ~300 | 4 | ✅ |
| 오디오 처리 (denoise/trim) | ~200 | 2 | ✅ |
| Pipeline 클래스 + 채점 | ~1,100 | 12+ | ✅ |

**폴더 분리 판단:** 분리 후에도 `pipe/` 내 약 15개 .py 파일이므로 서브폴더 구조는 과도 → **플랫 구조 유지** 결정.

### 7.2 분리 결과

#### 신규 모듈

| 파일 | 책임 | 핵심 함수/클래스 |
|---|---|---|
| `engine_utils.py` | 공유 유틸리티 (순환 import 방지 최하위 레이어) | `_normalize_word`, `_sum_word_durations`, `count_pauses_from_words`, `_count_word_tokens` |
| `speaker_analysis.py` | 화자 라벨 처리, 파트별 speaker annotation | `_get_word_speaker_label`, `_speaker_run_words`, `_dominant_speaker_label`, `smooth_word_speaker_labels`, `annotate_reference_part_speakers` |
| `text_processing.py` | 문장/턴 분할, 레퍼런스 파트 빌드, 짧은 파트 병합 | `split_into_sentences_with_timestamps`, `split_into_dialog_turns`, `merge_short_reference_parts`, `_build_reference_part` |
| `quality.py` | 품질 평가, 오디오 메트릭, 캡션 정렬 건강도, denoise 모드 선택 | `evaluate_caption_alignment_health`, `estimate_reference_audio_metrics`, `select_reference_denoise_mode`, `assess_reference_quality` |
| `audio_processing.py` | 분석용 디노이징(Track B), 구간 경계 정제(trim) | `denoise_for_analysis`, `trim_boundary_fragments` |
| `pipeline.py` | StyleEchoPipeline 클래스, 싱글턴, STT/Alignment, Prosody, 종합 채점 | `get_pipeline`, `StyleEchoPipeline` (전 메서드) |

#### 변경된 기존 파일

| 파일 | 변경 내용 |
|---|---|
| `engine.py` | 2,379줄 → 69줄. 하위 호환 re-export hub 로 전환. 기존 `from .engine import X` 그대로 동작. |
| `main.py` | import 를 새 모듈 직접 참조로 갱신 (`audio_processing`, `pipeline`, `quality`, `text_processing`) |
| `reference_service.py` | `engine_utils` 에서 직접 import, 중복 `_sum_word_durations` 제거 |

### 7.3 의존 그래프 (순환 없음)

```
engine_utils  ← speaker_analysis  ← text_processing
              ← audio_processing  ← quality (also ← speaker_analysis, text_processing)
                                  ← pipeline (also ← audio_processing, engine_utils)
```

- **최하위:** `engine_utils` — 어디에서든 안전하게 import 가능
- **중간:** `speaker_analysis`, `audio_processing` — 하위만 참조
- **상위:** `text_processing`, `quality` — 중간 레이어 참조
- **최상위:** `pipeline` — 품질·오디오 처리 모듈 참조

### 7.4 하위 호환 전략

`engine.py` 를 re-export hub 로 유지하여 기존 코드에서 `from .engine import X` 형태로
쓰던 모든 import 가 깨지지 않습니다. **신규 코드는 각 하위 모듈을 직접 import** 하는 것을 권장합니다.

### 7.5 후속 개선 사항

- `pipeline.py` (약 1,100줄) 는 여전히 큰 편이나, `StyleEchoPipeline` 클래스가
  모델 인스턴스 상태를 공유하므로 추가 분리 시 인터페이스 복잡도가 증가합니다.
  `evaluate()` 메서드만 별도 모듈로 분리하는 것은 향후 검토 가능합니다.
- `_truncate_engine.py` 임시 파일이 남아있으면 수동 삭제 필요.

---

## 8. short-part merge 구두점 보호 파라미터 추가

짧은 part 병합 동작을 튜닝하는 과정에서,
기존에는 `terminal punctuation(.?!)` 보호를 **threshold 로만 우회**할 수 있었고
전용 on/off 스위치는 없었습니다.

이를 보완하기 위해 아래 파라미터를 추가했습니다.

### 신규 파라미터

```yaml
reference:
  short_part_terminal_protection_enabled: true
```

### 의미

- `true`
  - 문장 끝 구두점이 있는 짧은 part 를 더 보수적으로 유지
  - `short_part_terminal_keep_min_words`
  - `short_part_terminal_keep_min_duration_sec`
    두 threshold 가 적용됨

- `false`
  - 구두점 유무를 무시하고 일반 short-part merge 규칙만 적용
  - 즉, terminal punctuation 보호 로직 자체를 비활성화

### 반영 위치

- `config.py`
  - `REFERENCE_SHORT_PART_TERMINAL_PROTECTION_ENABLED` 추가
- `text_processing.py`
  - `_should_merge_short_reference_part()` 내부에서
    terminal punctuation 보호 로직을 해당 플래그로 감싸도록 수정
- `config_default.yaml`
  - 기본값 및 설명 추가
- `config.yaml`
  - 사용자 설정 예시 추가
