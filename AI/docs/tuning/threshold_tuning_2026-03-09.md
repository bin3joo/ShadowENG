---
description: generate-reference threshold tuning recommendation set
---

# Threshold Tuning Guide

## 목적

이 문서는 `generate-reference`의 `reference_quality`, `speaker_mode`,
`dialog_mode` 판정을 튜닝하기 위한 추천 threshold 세트를 정리합니다.

현재 관찰된 결과 기준으로는 단일 화자 설명형 영상도 `risky`,
`speaker_uncertain`, `turn_segmented`로 다소 보수적으로 분류될 수
있습니다. 따라서 운영 목적에 따라 threshold를 구분해서 관리하는 것이
좋습니다.

## 현재 구현 상태 요약

### 현재 있는 것

- WhisperX STT / forced alignment
- 분석용 adaptive denoise
- 오디오 품질 추정 기반 `noise_level`
- heuristic 기반 `speaker_mode`
- pause 기반 `turn` 분할
- `reference_quality` = `good | risky | reject`

### 현재 없는 것

- 실제 화자분리 diarization 파이프라인
- 실제 source separation 기반 화자 분리
- `pyannote.audio` diarization 결과를 활용한 화자 단위 재구성
- word 단위 speaker label 부여

즉, 현재 `speaker_mode`는 **화자분리 결과가 아니라 heuristic 추정값**입니다.

## 현재 코드 기준 판정 로직

### 품질 관련 주요 threshold

- `reference.medium_noise_snr_db = 13.0`
- `reference.high_noise_snr_db = 7.0`
- `reference.medium_speech_ratio = 0.40`
- `reference.low_speech_ratio = 0.25`
- `reference.low_alignment_score = 0.40`
- `reference.low_alignment_ratio = 0.60`
- `reference.high_overlap_ratio = 0.42`
- `reference.medium_overlap_ratio = 0.26`
- `reference.speaker_shift_semitones = 7.0`
- `reference.turn_gap_sec = 0.85`
- `reference.dialog_turn_max_words = 16`

### 현재 speaker 관련 판단 방식

실제 diarization이 아니라 아래 heuristic을 사용합니다.

- part별 `f0_array`의 median 비교
- 인접 part 간 semitone shift 계산
- 단어 간 gap이 매우 짧은 경우 overlap risk 증가
- overlap 비율과 speaker shift 비율을 조합해
  `single_speaker_assumed`, `speaker_uncertain`,
  `multi_speaker_suspected` 판정

### 현재 dialog 관련 판단 방식

- 문장 분리 후
- pause gap, 문장부호, max words를 기준으로
- 필요 시 `turn` 단위로 재분할

## 최근 관찰 결과 해석

### 케이스 1

- `caption_align`
- `quality = risky`
- `denoise = moderate`
- `speaker = speaker_uncertain`
- `dialog = turn_segmented`
- `turn_count = 9`
- `noise_level = high`

설명형 콘텐츠인데도 `turn_segmented`가 강하게 걸린다면,
`turn_gap_sec`가 다소 낮거나 `dialog_turn_max_words`가 작을 가능성이 있습니다.

### 케이스 2

- `whisper_stt`
- `quality = risky`
- `speaker = speaker_uncertain`
- `dialog = sentence`
- `noise_level = high`

드라마/대화형 콘텐츠라면 `speaker_uncertain` 자체는 자연스럽지만,
단일 화자 콘텐츠에서도 자주 이렇게 나온다면 `speaker_shift_semitones`와
`medium_overlap_ratio`가 너무 민감할 수 있습니다.

## 추천 threshold 세트

아래 세트는 `config_default.yaml`의 `reference:` 블록에 바로 반영할 수
있도록 작성했습니다.

## Set A: Conservative Reject

고품질 단일 화자 레퍼런스만 엄격하게 통과시키고 싶은 경우입니다.

```yaml
reference:
  denoise_mode: "auto"
  allow_risky: false
  reject_on_high_overlap: true
  reject_on_low_alignment: true
  turn_gap_sec: 0.75
  dialog_turn_max_words: 10
  medium_noise_snr_db: 16.0
  high_noise_snr_db: 10.0
  medium_speech_ratio: 0.50
  low_speech_ratio: 0.35
  low_alignment_score: 0.50
  low_alignment_ratio: 0.40
  high_overlap_ratio: 0.28
  medium_overlap_ratio: 0.15
  speaker_shift_semitones: 3.8
```

## Boundary / Caption / Merge 파라미터 상세 가이드

이 섹션은 `generate-reference`에서 실제로 체감되는
`앞이 길게 남는 문제`, `뒤가 많이 잘리는 문제`,
`caption_align 오정렬`, `짧은 파트 과분할`을 어떤 설정으로 조정해야 하는지
정리한 것입니다.

### 1. 오디오 구간 패딩

#### `padding.audio_sec`

- 의미
  - 유튜브에서 레퍼런스 오디오를 다운로드할 때 요청 구간의 앞뒤로 추가 확보하는 시간입니다.
  - 현재 구현은 **앞뒤 대칭 패딩**입니다.

- 코드 영향 위치
  - `youtube_service.build_yt_dlp_command()`
  - `main.generate_reference()`에서 caption fetch에도 같은 값을 전달

- 값을 키우면
  - 앞/뒤 음성 보존량이 같이 늘어납니다.
  - 문장 시작이나 끝이 잘리는 문제는 줄어들 수 있습니다.
  - 대신 앞부분 불필요 음성이 더 많이 들어와 trim 의존도가 올라갑니다.

- 값을 줄이면
  - 앞뒤 모두 더 타이트하게 잘립니다.
  - 앞부분이 긴 문제는 완화될 수 있지만, 뒤 잘림이 더 심해질 수 있습니다.

- 현재 증상과의 관계
  - **앞이 길고 뒤가 많이 잘린다**면 이 값 하나만으로는 완벽히 해결하기 어렵습니다.
  - 왜냐하면 현재는 앞/뒤를 따로 조정하는 파라미터가 없고, 둘 다 동시에 움직이기 때문입니다.

### 2. 앞부분 trim 관련

#### `trimming.front_score_threshold`

- 의미
  - 앞부분 단어를 잘라낼 때 사용하는 alignment 저신뢰 기준입니다.
  - 앞쪽 단어가 소문자 시작이면서 이 값보다 score가 낮으면 제거 후보가 됩니다.

- 값을 키우면
  - 더 많은 앞부분 단어가 저신뢰로 간주되어 **앞 trim이 강해집니다**.
  - 즉, **앞이 길게 남는 문제를 줄이는 방향**입니다.

- 값을 줄이면
  - 앞 trim이 덜 공격적이 됩니다.
  - 문장 시작이 잘려 나가는 문제는 줄지만, 불필요한 앞 단어가 더 남을 수 있습니다.

- 실무 팁
  - 앞이 미묘하게 길다면 먼저 `0.6 -> 0.65` 정도로 소폭 올려보는 것이 안전합니다.

### 3. 뒤부분 trim 관련

#### `trimming.back_score_threshold`

- 의미
  - 마지막 단어가 오디오 끝 근처에 있고 score가 낮으면 뒤를 잘라내는 기준입니다.

- 값을 키우면
  - 더 많은 마지막 단어가 저신뢰로 분류되어 **뒤 trim이 강해집니다**.
  - 즉, 뒤가 많이 잘리는 현상이 심해질 수 있습니다.

- 값을 줄이면
  - 뒤 trim이 완화됩니다.
  - 즉, **뒤를 덜 자르고 더 보존하는 방향**입니다.

- 실무 팁
  - 뒤가 과하게 잘리면 먼저 `0.45 -> 0.40` 또는 `0.38` 정도로 내려보는 것을 추천합니다.

#### `trimming.boundary_gap_sec`

- 의미
  - 마지막 단어가 오디오 끝에서 얼마나 가까우면 `경계에 붙은 잘림 가능성`으로 볼지를 정하는 값입니다.

- 값을 키우면
  - 더 넓은 구간이 경계 근접으로 간주되어 **뒤 trim이 더 자주 발생**합니다.

- 값을 줄이면
  - 경계 근접 판정이 덜 민감해져 **뒤가 덜 잘립니다**.

- 실무 팁
  - 뒤가 많이 잘리면 `0.2 -> 0.12 ~ 0.15` 범위를 먼저 시험해볼 만합니다.

### 4. caption 선택 관련

#### `padding.caption_min_entry_overlap_ratio`

- 의미
  - caption entry가 현재 padded window에 충분히 겹치는지 판단하는 최소 overlap 비율입니다.

- 값을 키우면
  - 경계에 살짝 걸친 caption entry가 덜 포함됩니다.
  - 앞 문장 contamination 방지에 유리합니다.

- 값을 줄이면
  - 더 많은 caption entry가 포함됩니다.
  - 문맥 확보에는 유리하지만 오정렬 위험이 커질 수 있습니다.

- 현재 역할
  - `entry midpoint`가 window 안에 있거나,
  - overlap ratio가 이 값을 넘을 때만 포함합니다.

### 5. caption_align fallback 관련

#### `alignment.caption_min_surviving_word_ratio`

- 의미
  - caption 단어 중 alignment 후 살아남은 단어 비율이 너무 낮으면 fallback reason을 추가하는 기준입니다.

- 값을 키우면
  - caption_align 품질에 더 엄격해집니다.
  - Whisper STT fallback이 더 자주 발생합니다.

#### `alignment.caption_strong_min_surviving_word_ratio`

- 의미
  - 매우 낮은 surviving ratio일 때 즉시 fallback하기 위한 강한 기준입니다.

#### `alignment.caption_front_window_words`

- 의미
  - 앞부분 저신뢰 비율을 계산할 때 보는 초기 단어 수입니다.

#### `alignment.caption_max_front_low_conf_ratio`

- 의미
  - 앞부분 단어 중 low-confidence 비율이 이 값을 넘으면 fallback reason이 추가됩니다.

#### `alignment.caption_max_leading_gap_sec`

- 의미
  - 첫 surviving word 시작이 너무 늦으면 비정상 정렬로 보고 fallback reason을 추가하는 기준입니다.

#### `alignment.caption_fallback_min_reason_count`

- 의미
  - fallback reason이 몇 개 이상 모이면 실제 Whisper STT fallback을 실행할지 정하는 기준입니다.

### 6. 짧은 파트 병합 관련

#### `reference.min_part_duration_sec`

- 의미
  - 이 값보다 짧은 파트만 병합 후보가 됩니다.

- 값을 키우면
  - 더 많은 짧은 파트가 병합 후보가 됩니다.

- 값을 줄이면
  - 더 적은 파트만 병합 후보가 됩니다.

#### `reference.max_part_merge_gap_sec`

- 의미
  - 짧은 파트를 앞/뒤 파트와 병합할 때 허용하는 최대 간격입니다.

- 값을 키우면
  - 띄엄띄엄 떨어진 짧은 파트도 병합되기 쉬워집니다.

- 값을 줄이면
  - 가까운 파트끼리만 병합됩니다.

#### `reference.short_part_fragment_max_words`

- 의미
  - 이 단어 수 이하이면 파편(fragment)로 간주합니다.

#### `reference.short_part_keep_min_words`

- 의미
  - 짧아도 이 단어 수 이상이면 독립 파트로 유지합니다.

#### `reference.short_part_terminal_keep_min_words`

- 의미
  - 문장 종료 구두점이 있어도 최소 이 단어 수는 되어야 독립 파트로 유지합니다.
  - 예: `Tate.` 같은 1단어 파트는 구두점이 있어도 병합될 수 있습니다.

#### `reference.short_part_terminal_keep_min_duration_sec`

- 의미
  - 문장 종료 구두점이 있는 초단문이라도 이 시간 이상이면 독립 파트로 유지합니다.

#### `reference.short_part_merge_max_wpm`

- 의미
  - 짧고 느린 파트를 파편으로 보아 병합 대상으로 올리는 기준입니다.

## 현재 증상별 추천 조정 순서

### 증상 A: 앞이 미묘하게 길다

우선순위:

- `trimming.front_score_threshold` 소폭 증가
- 필요하면 `padding.audio_sec` 소폭 감소

추천 시작값:

```yaml
trimming:
  front_score_threshold: 0.65
```

### 증상 B: 뒤가 많이 잘린다

우선순위:

- `trimming.back_score_threshold` 감소
- `trimming.boundary_gap_sec` 감소
- 그래도 부족하면 `padding.audio_sec` 증가

추천 시작값:

```yaml
trimming:
  back_score_threshold: 0.40
  boundary_gap_sec: 0.15
```

### 증상 C: 앞은 길고 뒤는 잘린다

현재 구조에서는 아래 순서가 가장 현실적입니다.

```yaml
padding:
  audio_sec: 1.0

trimming:
  front_score_threshold: 0.65
  back_score_threshold: 0.40
  boundary_gap_sec: 0.15
```

이 조합은

- 앞은 더 공격적으로 trim 하고
- 뒤는 덜 잘라내도록

균형을 맞추는 방식입니다.

단, 현재는 `front padding`과 `back padding`을 따로 조정하는 구조가 아니므로,
비대칭 문제를 더 정밀하게 잡으려면 나중에
`padding.audio_front_sec`, `padding.audio_back_sec` 같은 분리 설정을 두는 것이
가장 깔끔합니다.

### 추천 상황

- 교육용/쉐도잉용 고품질 레퍼런스만 채택
- noisy clip, drama clip을 대부분 제외하고 싶을 때

### 장점

- 품질 하한선이 명확함
- 잘못된 레퍼런스 생성 확률이 낮음

### 단점

- 정상 단일 화자 영상도 과하게 reject될 수 있음
- `risky` 비율이 매우 높아질 수 있음

## Set B: Balanced Default

현재 시스템의 1차 운영 기본값으로 추천하는 세트입니다.
현재 값보다 약간 완화해서 false positive를 줄이는 방향입니다.

```yaml
reference:
  denoise_mode: "auto"
  allow_risky: true
  reject_on_high_overlap: true
  reject_on_low_alignment: true
  turn_gap_sec: 0.85
  dialog_turn_max_words: 16
  medium_noise_snr_db: 13.0
  high_noise_snr_db: 7.0
  medium_speech_ratio: 0.40
  low_speech_ratio: 0.25
  low_alignment_score: 0.40
  low_alignment_ratio: 0.60
  high_overlap_ratio: 0.42
  medium_overlap_ratio: 0.26
  speaker_shift_semitones: 7.0
```

### 추천 상황

- 설명형 유튜브, 인터뷰, 일반 강연까지 폭넓게 수용
- `reject`보다 `risky + warning` 중심 운영

### 기대 효과

- 단일 화자 설명 영상의 `speaker_uncertain` 과검출 감소
- pause가 많은 설명형 영상의 `turn_segmented` 과검출 감소
- `high_noise` 판정 빈도 완화

## Set C: Recall First

레퍼런스를 최대한 많이 살리고, 후단에서 사람이 보거나 추가 필터링할
경우에 مناسب한 세트입니다.

```yaml
reference:
  denoise_mode: "auto"
  allow_risky: true
  reject_on_high_overlap: false
  reject_on_low_alignment: false
  turn_gap_sec: 1.00
  dialog_turn_max_words: 18
  medium_noise_snr_db: 12.0
  high_noise_snr_db: 6.0
  medium_speech_ratio: 0.35
  low_speech_ratio: 0.20
  low_alignment_score: 0.35
  low_alignment_ratio: 0.70
  high_overlap_ratio: 0.50
  medium_overlap_ratio: 0.32
  speaker_shift_semitones: 6.5
```

### 추천 상황

- 레퍼런스 확보량이 중요할 때
- 사후 검토 또는 재평가 단계가 따로 있을 때

### 장점

- reject가 크게 줄어듦
- 다양한 영상 타입을 일단 수집 가능

### 단점

- 다중 화자/노이즈 레퍼런스가 더 많이 통과함
- 후단 평가 품질이 흔들릴 수 있음

## 실무 추천

현재 상태에서는 **Set B: Balanced Default**를 가장 추천합니다.

이유는 아래와 같습니다.

- 최근 테스트에서 `noise_level=high`, `speaker_uncertain`이 다소 자주
  나올 가능성이 보임
- 아직 실제 diarization이 없어서 speaker heuristic은 원래 오탐이 있음
- 따라서 early reject를 너무 강하게 두면 usable clip도 과하게 버릴 수 있음

## 항목별 튜닝 우선순위

### 1. `turn_gap_sec`

가장 먼저 조정할 값입니다.

- 현재 `0.65`는 설명형 콘텐츠에서 `turn_segmented`를 과하게 만들 수 있음
- 추천 시작값: `0.85`

### 2. `dialog_turn_max_words`

- 현재 `12`는 비교적 짧음
- 설명형 문장이 긴 영상에서는 `16` 정도가 더 안정적

### 3. `speaker_shift_semitones`

- 현재 `4.5`는 억양 변화가 큰 단일 화자도 민감하게 잡을 수 있음
- 추천 시작값: `5.5`

### 4. `medium_overlap_ratio`

- 현재 `0.2`는 빠른 연설, 자막 alignment 오차에도 민감할 수 있음
- 추천 시작값: `0.26`

### 5. `low_alignment_ratio`

- 현재 `0.5`는 caption align 품질 편차에 따라 쉽게 risky/reject로 갈 수 있음
- 추천 시작값: `0.6`

## 단계별 튜닝 절차

### 1단계

아래 3개만 먼저 바꿉니다.

```yaml
reference:
  turn_gap_sec: 0.85
  dialog_turn_max_words: 16
  speaker_shift_semitones: 5.5
```

### 2단계

여전히 `speaker_uncertain`이 많으면 다음을 추가로 조정합니다.

```yaml
reference:
  medium_overlap_ratio: 0.26
  high_overlap_ratio: 0.42
```

### 3단계

여전히 `risky/reject`가 과하면 alignment 기준을 완화합니다.

```yaml
reference:
  low_alignment_score: 0.40
  low_alignment_ratio: 0.60
```

### 4단계

노이즈가 과하게 높게 잡히면 SNR 기준을 완화합니다.

```yaml
reference:
  medium_noise_snr_db: 13.0
  high_noise_snr_db: 7.0
  medium_speech_ratio: 0.40
  low_speech_ratio: 0.25
```

## 화자분리 프로세스 관련 결론

현재 코드베이스에는 **실제 화자분리 프로세스가 없습니다.**

정확히는 다음 상태입니다.

- `speaker_mode`는 존재함
- `speaker_risk`도 존재함
- 하지만 둘 다 **diarization output 기반이 아니라 heuristic 기반 메타데이터**임
- `pyannote.audio` 또는 WhisperX diarization을 실제로 실행하는 코드는 현재 없음

즉, 현재는 **화자분리 준비 전 단계의 risk estimation**이라고 보는 것이 맞습니다.

## 다음 구현 권장 순서

### 1순위

- `pyannote.audio` diarization 실제 연결
- segment별 speaker label 추출
- part별 dominant speaker 계산

### 2순위

- overlapping speech 비율을 diarization 기반으로 재계산
- heuristic overlap 대신 실제 speaker overlap 반영

### 3순위

- 레퍼런스 생성 시 dominant speaker만 남기는 옵션 추가
- multi-speaker clip에서 단일 화자 구간만 우선 채택

## 권장 운영안

현재 바로 운영한다면 아래 조합을 추천합니다.

```yaml
reference:
  denoise_mode: "auto"
  allow_risky: true
  reject_on_high_overlap: true
  reject_on_low_alignment: true
  turn_gap_sec: 0.85
  dialog_turn_max_words: 16
  medium_noise_snr_db: 13.0
  high_noise_snr_db: 7.0
  medium_speech_ratio: 0.40
  low_speech_ratio: 0.25
  low_alignment_score: 0.40
  low_alignment_ratio: 0.60
  high_overlap_ratio: 0.42
  medium_overlap_ratio: 0.26
  speaker_shift_semitones: 5.5
```

이 세트는 현재 heuristic 구조에서 false positive를 줄이면서도,
너무 느슨해지지 않도록 맞춘 균형안입니다.
