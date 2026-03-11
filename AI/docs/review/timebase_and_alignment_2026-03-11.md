# Timebase and Alignment Update

## 목적

- 레퍼런스 생성 시 내부 패딩을 유지하면서도 사용자에게 노출되는 시간 기준을 요청 구간 기준으로 통일합니다.
- evaluate 경로에서 연음/축약 표현이 레퍼런스와 사용자 단어 구조 차이 때문에 불필요하게 불일치 처리되는 문제를 줄입니다.

## 변경 요약

### 1. 레퍼런스 시간 기준 A 적용

기준 A는 요청 구간을 0초로 보는 상대 시간 기준입니다.

예시:

- 요청 구간: `108.0 ~ 135.0`
- 내부 정렬용 다운로드 구간: `107.0 ~ 136.0` (`padding=1.0`)
- 기존 문제: 정렬 결과 timestamp 가 padded clip 기준으로 남아 실제 영상 기준과 혼동됨
- 변경 후: 최종 `reference.json`의 `word_timestamps`, `parts[*].start_sec/end_sec`, `full_audio.wav`, part 오디오가 모두 요청 구간 기준으로 정렬됨

즉 내부 패딩은 정렬 안정성을 위해 유지하지만, 외부에 보이는 산출물은 패딩 영향을 제거한 요청 구간 기준으로 통일합니다.

### 2. 평가용 연음/축약 canonical alignment 재구성

기존 evaluate 경로는 `_normalize_word()`로 소문자화와 구두점 제거만 수행했습니다.
이 방식은 아래 표현에서 정렬 손실이 발생할 수 있습니다.

- `gonna` ↔ `going to`
- `wanna` ↔ `want to`
- `gotta` ↔ `got to`
- `lemme` ↔ `let me`
- `gimme` ↔ `give me`
- `I'm` ↔ `I am`

이번 변경에서는 `REDUCTION_PATTERNS`를 평가 정렬 canonicalization 에도 연결해,
축약형과 원형을 공통 token sequence 로 정규화한 뒤 `_align_user_words_to_ref()`에서 monotonic matching 하도록 재구성했습니다.

## 코드 변경 포인트

### `services/reference_service.py`

- 패딩 포함 alignment 결과를 요청 구간 기준으로 rebasing 하는 `_rebase_reference_words()` 추가
- 요청 구간 기준 오디오를 만드는 `_slice_audio_segment()` 추가
- 품질 평가, feature 추출, part 오디오 export, `full_audio.wav` 저장 모두 요청 구간 기준 오디오로 통일
- `attach_part_analysis()` 호출 시 speech start 를 rebased word 기준으로 전달

### `integrations/io_utils.py`

- `persist_reference_audio()`가 padded source 파일 복사 대신 request-relative audio array 를 `full_audio.wav`로 저장하도록 변경

### `domain/processing/engine_utils.py`

- `_canonicalize_tokens()` 추가
- `REDUCTION_PATTERNS` 기반 canonical phrase map 추가
- `_normalize_word()`가 canonical token 기반 문자열을 반환하도록 변경

### `pipeline.py`

- `_align_user_words_to_ref()`를 canonical token sequence 기반 monotonic matcher 로 재구성
- 1 token ↔ N token, 축약 ↔ 원형 매칭 시 merged timing span 을 유지

## 기대 효과

- `reference.json`의 시간값과 저장 오디오의 기준이 일치합니다.
- 사용자가 실제 요청 구간 기준으로 part 시간과 오디오를 해석할 수 있습니다.
- evaluate 에서 축약/연음 표현으로 인한 과도한 mismatch 를 줄일 수 있습니다.
- rhythm / pitch contour feedback 에서 canonicalized alignment 결과를 활용할 수 있습니다.

## 주의 사항

- top-level `start_sec`, `end_sec`는 여전히 원래 YouTube 요청 구간의 절대 시간입니다.
- `parts[*].start_sec/end_sec`와 `word_timestamps[*].start/end`는 요청 구간 내부 상대 시간입니다.
- evaluate 결과 저장 파일명은 아직 `evaluate_result.json` 단일 파일이므로 part 별 비교 실험 시 마지막 결과가 덮어써질 수 있습니다.

## 권장 검증

### 레퍼런스 생성

```bash
python -m test.test_api generate "https://www.youtube.com/shorts/ZgfgOIaoWiA" 0.0 41.0
```

확인 포인트:

- `reference.json`의 첫 word timestamp 가 0초 근처인지
- `parts[*].start_sec/end_sec`가 `full_audio.wav` 길이와 자연스럽게 맞는지
- `ref_audio/parts/*.wav`가 part 시간과 일치하는지

### 평가

```bash
python -m test.test_api evaluate "./역삼동.m4a" --ref "./test/result/ZgfgOIaoWiA/meta/reference.json" --part 1
```

추가 확인 포인트:

- `gonna`, `wanna`, `I'm`, `gotta` 류 표현에서 단어 누락이 줄어드는지
- `word_level_feedback`의 `missed` 수가 과도하게 높지 않은지
- rhythm / pitch feedback 에서 canonicalized merged span 이 자연스럽게 보이는지
