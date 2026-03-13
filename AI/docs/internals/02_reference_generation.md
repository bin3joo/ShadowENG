# 2. 레퍼런스 생성 파트 (Reference Generation)

클라이언트가 요청한 YouTube 비디오의 특정 구간 오디오를 바탕으로, 유저가 따라 말할 수 있는 **정답(Reference) 데이터**를 생성하는 계층입니다. `services/reference_service.py` 내의 `generate_reference()` 함수가 오케스트레이션을 담당합니다.

## 최근 변경사항 요약

1. **원본 / VR 역할 분리**
   - STT / forced alignment 및 품질 평가는 원본 오디오 기준으로 수행합니다.
   - prosody 후보 추출은 원본과 VR 오디오를 함께 사용할 수 있습니다.

2. **병렬 처리 확대**
   - caption fetch ↔ audio download
   - VR ↔ STT / alignment
   - original prosody ↔ VR prosody
   - Gemini 번역 ↔ prosody attach / quality assessment

3. **Gemini 호출 시점 조정**
   - `sentence_data` 생성 직후 Gemini 요청을 시작하고,
     그 동안 prosody attach 및 품질 평가를 진행합니다.

4. **VR 설정 토글 추가**
   - `vocal_remover.enabled: false` 이면 reference 생성에서
     vocal separation, VR prosody 추출, source gating 비교를 모두 스킵합니다.
   - 이 경우 prosody는 원본 오디오만 사용합니다.

## 시스템 흐름도 (Execution Flow)

### 1단계: 준비
1. `get_pipeline()`으로 WhisperX 파이프라인 싱글턴을 확보합니다.
2. 임시 작업 디렉터리와 WAV 출력 경로를 생성합니다.
3. 다운로드 시에는 항상 `AUDIO_PADDING_SEC`를 포함한 padded 오디오를 확보하도록 준비합니다.

### 2단계: 외부 자원 획득 병렬화 (I/O)
이 단계는 `ThreadPoolExecutor`로 병렬 처리됩니다.

1. **자막 조회 (`fetch_youtube_captions`)**
   - manual / auto / none 상태를 판별합니다.
   - manual 자막이면 padded 범위를 기준으로 텍스트를 구성합니다.
   - auto 자막은 현재 STT fallback 신호로만 사용합니다.

2. **오디오 다운로드 (`download_reference_audio`)**
   - `yt-dlp` Python API로 padded 오디오를 WAV로 저장합니다.
   - 이후 실제 분석은 요청 구간 기준으로 다시 잘라 사용합니다.

### 3단계: STT/정렬과 VR 병렬화
오디오 다운로드가 끝나면, 다음 두 갈래가 동시에 시작됩니다.

1. **보컬 분리 (`separate_vocals`)**
   - BGM/효과음이 섞인 YouTube 오디오에서 보컬만 분리합니다.
   - VR 실패 시 원본 오디오 경로를 그대로 반환합니다.

2. **원본 오디오 기반 텍스트 정렬 / STT**
   - **Fast Path**: manual caption이 있으면 `pipeline.align_text_to_audio(actual_audio, caption_text)`를 수행합니다.
   - **Fallback**: 정렬 품질이 낮거나 단어 타임스탬프가 비어 있으면 `pipeline.extract_whisper_stats(actual_audio)`로 재시도합니다.
   - **Slow Path**: caption이 없으면 처음부터 `pipeline.extract_whisper_stats(actual_audio)`를 수행합니다.

중요한 점은 다음과 같습니다.

- **STT / forced alignment는 원본 오디오**를 사용합니다.
- **VR 오디오는 이 단계에서 텍스트 인식에 사용하지 않습니다.**

### 4단계: 텍스트 및 타임스탬프 정제
1. `trim_boundary_fragments()`로 요청 구간 경계에 걸친 fragment 단어를 정리합니다.
2. `sanitize_word_timestamps()`로 word timestamp 구조를 정리합니다.
3. `_rebase_reference_words()`로 padded 오디오 기준 타임스탬프를 요청 구간 기준으로 재매핑합니다.
4. 최종 `final_script`, `final_words`, `trimmed_word_count`를 확정합니다.

### 5단계: 요청 구간 오디오 슬라이싱
1. **원본 request 오디오**를 요청 구간 기준으로 잘라 `request_audio`를 만듭니다.
2. **VR request 오디오**를 같은 구간으로 잘라 `feature_request_audio`를 만듭니다.
3. `estimate_reference_audio_metrics()`로 reference 품질 메타데이터를 계산합니다.
4. `select_reference_denoise_mode_from_metrics()`로 prosody 분석 시 사용할 denoise mode를 결정합니다.

### 6단계: 원본 / VR prosody 병렬 추출
speech 구간만 다시 잘라낸 뒤, 두 소스에서 prosody를 병렬 추출합니다.

1. **원본 prosody**
   - `pipeline.extract_prosody_features(cropped_original_feature_audio, ...)`

2. **VR prosody**
   - `pipeline.extract_prosody_features(cropped_feature_audio, ...)`

이후 `pipeline.select_reference_prosody_sources()`가 gating 규칙으로 최종 소스를 결정합니다.

- `f0`와 `rms`는 **독립적으로 선택**될 수 있습니다.
- 예: `f0=vr`, `rms=original`

### 7단계: sentence_data 생성
1. `split_into_sentences_with_timestamps()`로 `final_script`와 `final_words`를 part 단위로 분할합니다.
2. 이 시점의 `sentence_data`는 번역 입력의 기준 구조가 됩니다.

### 8단계: Gemini 번역과 prosody/quality 병렬화
이 단계도 병렬 처리됩니다.

1. **Gemini 번역 시작**
   - `translate_reference_parts_with_gemini(final_script, deepcopy(sentence_data))`
   - `sentence_data`는 이후 in-place로 수정되므로 경쟁 상태 방지를 위해 deep copy를 넘깁니다.

2. **메인 스레드에서 prosody attach 및 품질 평가**
   - `attach_part_analysis()`로 각 part에 `features.f0_array`, `features.rms_array`, `pause_count`를 붙입니다.
   - `assess_reference_quality()`로 `good / risky / reject`를 판단합니다.
   - `_apply_speaker_risk_policy()`로 part별 speaker risk를 보정합니다.

3. **reject 여부 판단**
   - reject 또는 risky-but-not-allowed이면 HTTP 422를 반환합니다.
   - 이 경우 Gemini future는 cancel을 시도하지만, 이미 외부 호출이 시작된 경우 실제 호출 자체가 완전히 중단되지는 않을 수 있습니다.

### 9단계: 번역 결과 병합
reject가 아니면 Gemini 결과를 받아 후처리를 이어갑니다.

1. `translation_future.result()`로 번역 결과를 회수합니다.
2. 번역 후 part 구조(`translation_result.parts`)에 다시 `attach_part_analysis()`를 적용합니다.
3. `annotate_reference_part_speakers()`로 part별 화자 메타데이터를 부여합니다.
4. `_apply_speaker_risk_policy()`를 다시 적용해 번역 후 part 구조에도 정책을 반영합니다.

### 10단계: 저장 및 응답 생성
1. 저장용 오디오에만 `peak_normalize_audio()`를 적용합니다.
2. `prepare_reference_audio_dir()`로 저장 디렉터리를 준비합니다.
3. `persist_reference_audio()`로 전체 reference 오디오를 저장합니다.
4. `_export_part_audio_files()`로 part 오디오를 개별 저장합니다.
5. `build_reference_response()`로 최종 API 응답 payload를 생성합니다.

### 11단계: 임시 파일 정리
성공 시 `BackgroundTasks`에 cleanup 작업을 등록합니다.

- `remove_file(actual_audio)`
- `remove_dir(tmp_dir)`

실패 시에도 `finally` 블록에서 실패한 작업의 임시 자원을 정리합니다.

## 현재 병렬 처리 요약

현재 `generate_reference()`는 다음 구간을 병렬화하고 있습니다.

1. **caption fetch** ↔ **audio download**
2. **VR** ↔ **STT / forced alignment**
3. **original prosody extraction** ↔ **VR prosody extraction**
4. **Gemini translation** ↔ **part prosody attach / quality assessment**

## 오디오 소스 사용 원칙

- **STT / forced alignment**: 원본 오디오
- **reference 품질 메트릭 / 저장용 오디오 기준**: 원본 오디오
- **prosody 후보 추출**: 원본 + VR 둘 다
- **최종 F0 / RMS 선택**: gating 기반 source selection

## 주의할 점

1. VR와 WhisperX가 모두 GPU를 사용할 경우 장비 상황에 따라 속도 이점이 줄거나 VRAM 경합이 생길 수 있습니다.
2. Gemini 호출은 품질 reject 이전에 시작되므로, reject 요청에서도 일부 번역 호출 비용이 발생할 수 있습니다.
3. sentence_data는 in-place 수정 함수가 있으므로 병렬 구간에서는 shared object를 직접 넘기지 않아야 합니다.
