# 2. 레퍼런스 생성 파트 (Reference Generation)

클라이언트가 요청한 YouTube 비디오의 특정 구간 오디오를 바탕으로, 유저가 따라 말할 수 있는 **정답(Reference) 데이터**를 생성하는 계층입니다. `services/reference_service.py` 내의 `generate_reference()` 함수가 오케스트레이션을 담당합니다.

## 시스템 흐름도 (Execution Flow)

### 1단계: 외부 자원 획득 (I/O)
*   **자막 가져오기 (`fetch_youtube_captions`)**: YouTube Data API 또는 타사 라이브러리를 통해 수동(manual) 자막 혹은 자동(auto) 생성 자막을 가져옵니다.
*   **오디오 다운로드 (`download_reference_audio`)**: `yt-dlp`를 사용하여 해당 구간의 오디오를 고품질(WAV)로 다운로드합니다. 문맥 파악을 위해 설정된 마진(`AUDIO_PADDING_SEC`)만큼 앞뒤로 더 길게 가져옵니다.

### 2단계: 텍스트 정렬 및 STT 수행 (`pipeline.py`)
이 단계에서는 WhisperX 모델을 통해 실제 음성과 텍스트의 타임스탬프를 정합합니다.
*   **Fast Path (자막이 있는 경우)**: `pipeline.align_text_to_audio()`
    자막이 유효하고 동기화(Alignment)가 훌륭하다면 강제 정렬(Forced Alignment)만 빠르게 수행합니다.
*   **Slow Path (임의 실패 또는 자막이 없는 경우)**: `pipeline.extract_whisper_stats()`
    자막 정렬이 실패하거나(`caption_fallback`), 자막 자체가 아예 없다면 무거운 오디오 STT(Speech-to-Text)를 처음부터 다시 수행합니다.

### 3단계: 정제 과정 (Processing)
가져온 원시 데이터들을 교육적 활용을 위해 깨끗하게 가공합니다.
1.  **볼륨 정규화 (`peak_normalize_audio`)**: 소산/노이즈 왜곡을 방지하기 위해 오디오 볼륨을 디스토션 없이 최댓값 기반(Peak)으로 정규화합니다.
2.  **경계 정리 (`trim_boundary_fragments`)**: 요청한 시간 구간 앞뒤에 걸쳐있는 "잘려나간 불완전한 단어(Fragment)"들을 신뢰도 점수 기반으로 깎아냅니다.
3.  **메트릭 분석 (`estimate_reference_audio_metrics`)**: 해당 텍스트의 백그라운드 노이즈 SNR이나 쉼표 등을 미리 분석합니다.
4.  **억양/음압 분석 (`extract_prosody_features`)**: `librosa`와 `noisereduce`를 활용하여 F0(피치) 및 RMS(볼륨 에너지) 파형 피처를 연속된 배열로 뽑아냅니다.
5.  **문장 및 턴 분할 (`split_into_sentences_with_timestamps`, `split_into_dialog_turns`)**: 
    전체 텍스트를 문법적인 기준 단위(Sentence)나, 대화의 휴지기(Pause)/화자 변경점을 기준으로 학습할 수 있는 Chunk(Part)로 분리합니다.

### 4단계: 품질 평가 및 보완 (Quality & Metadata)
*   **화자 분석 (`annotate_reference_part_speakers`)**: `pyannote.audio`를 통해 해당 파트에 화자가 몇 명인지, 누가 주요 화자인지 분석하여 리포팅합니다(Speaker Diarization).
*   **품질 평가 (`assess_reference_quality`)**: AI가 보기에 레퍼런스가 따라 말하기에 너무 열악하거나(Overlap 시끄러움, 노이즈 극심) 정렬이 깨졌다면 품질을 판별합니다 (`good`, `risky`, `reject`). 극악인 경우 Exception을 던집니다.
*   **번역 (`translate_reference_parts_with_gemini`)**: Gemini 모델을 사용하여 최종 영어 스크립트와 각 분리된 Chunk 들의 자연스러운 한국어 해석 미치 핵심 표현 데이터를 병렬로 추출합니다.

### 5단계: 결과물 저장
모든 분석이 끝나면, 오디오 파일을 임시 폴더에서 영구 폴더로 이동복사하고(`persist_reference_audio`, `export_part_audio`), 최종 JSON (`build_reference_response`)을 API 계층으로 반환합니다.
이후 `BackgroundTasks`에 의해 잡다한 찌꺼기 파일들은 별도 스레드에서 안전하게 정리됩니다.
