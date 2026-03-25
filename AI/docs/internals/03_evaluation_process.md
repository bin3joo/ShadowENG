# 3. 유저 발화 평가 파트 (Evaluation Process)

이 단계는 앱이나 웹 프론트엔드에서 넘어온 유저의 목소리와, 미리 계산되어 있던 레퍼런스(Reference JSON) 데이터를 1:1로 비교하여 다차원적인 점수를 산출하는 핵심 계층입니다. 
`services/evaluation_service.py` 모듈과 `domain/scoring/aggregator.py`의 `run_full_evaluation()` 함수가 주도합니다.

## 시스템 흐름도 (Execution Flow)

### 1단계: 준비 (Initialization)
1. **유저 오디오 획득**: 사용자가 멀티파트 파일, 일반 HTTP/HTTPS URL, 또는 **S3 URL (`s3://bucket/key`)** 형태로 넘긴 오디오를 디스크에 저장(혹은 캐싱)합니다. AWS S3 연동을 위해 `boto3`를 사용하며 관련 자격 증명은 환경 변수 또는 `config.yaml`을 통해 관리됩니다.
2. **레퍼런스 복원**: DB 또는 클라이언트로부터 전달받은 `final_script`(원문), `word_timestamps`(단어 좌표셋), `features/f0_array, rms_array`(기준점 피처)를 메모리에 Numpy 배열 등으로 재복원합니다.
3. **오디오 로드 및 STT용 정규화**: 유저 오디오를 `librosa.load`로 로드한 뒤, `peak_normalize_audio`를 적용한 **정규화 임시 WAV**를 생성합니다. 이 파일은 STT 전용으로 사용됩니다.

### 2단계: (선택) 유저 오디오 VR 배경음 분리
`config.EVALUATION_USER_VR_ENABLED`가 `true`일 경우, 사용자 오디오에 `audio-separator`를 통한 **보컬 분리(VR)**를 적용합니다.
*   카페, 길거리 등 배경 소음이 심한 환경에서 녹음된 오디오에서 순수한 목소리만 추출하여 이후 프로소디 추출에 투입합니다.
*   VR 적용 시, 디노이즈(`denoise=True`)는 자동 비활성됩니다 (VR 자체가 강력한 디노이징이므로).

### 3단계: 유저 오디오 분석 (`extract_whisper_stats`)
**Peak 정규화된 임시 WAV**를 입력으로 WhisperX STT를 수행합니다. 볼륨이 작은 녹음에서도 Whisper의 환각(Hallucination)을 방지하고 인식률을 높입니다.
*   분석 결과로 유저가 실제로 내뱉은 **발화 내용 스트링(`text`)**, 전체 묵음 횟수(`pause_count`), 실제 유효 발성 시간(`active_speech_sec`), **단어 타임스탬프(`word_timestamps`)**가 도출됩니다.
*   STT 완료 후 임시 WAV는 즉시 삭제됩니다.

### 4단계: 논리적 1:1 매핑 (Alignment Sync)
레퍼런스가 요구하는 단어 개수 및 텍스트 구조와 유저가 말한 단어 구조가 100% 일치하지 않을 수 있습니다. 
따라서 `domain/scoring/word_alignment.py`의 `_align_user_words_to_ref` 함수를 사용하여 유저의 단어 타임스탬프를 **레퍼런스가 기대하는 단어 리스트와 매칭**시킵니다.
* 못 말한 단어는 누락(Missed) 처리하고 타임스탬프를 비웁니다.
* 억양이나 발음 점수는 이 매칭된 좌표를 바탕으로 1:1로 비교합니다.

### 5단계: 유저 프로소디 피처 추출 (Smart Cropping + Baseline 0 Fixed)
**원본(Raw) 오디오**(또는 VR 적용 오디오) 배열에서 F0(피치)와 RMS(볼륨) 곡선을 도출합니다.
1.  STT로 파악된 사용자의 **첫 단어 시작~마지막 단어 끝** 구간만 잘라냅니다 (**Smart Cropping**).
2.  `domain/prosody/feature_extraction.py`의 `extract_prosody_features`가 F0/RMS를 추출합니다.
3.  F0에는 pyin `voiced_flag` 기반 **무성음 0.0 마스킹** 및 **메디안 필터(kernel=5)** 적용.
4.  RMS에는 하위 15% 이하 극소 에너지를 0.0으로 강제 고정 (**Baseline 0 Fixed**).
5.  정규화된 `[f0_norm, rms_norm]` 벡터를 구축합니다.

### 6단계: 개별 스코어 추출 단계
7가지의 세분화된 스코어를 `domain/scoring/` 내 개별 모듈로 추출합니다 *(상세 수식은 `04_scoring_logic.md` 참고)*.
1.  **단어 정확도 (Word Accuracy)**: `jiwer` 패키지를 사용해 WER(Word Error Rate) 계산
2.  **속도 유사도 (Speed)**: `user_active_time`과 `ref_active_time`의 비율(Ratio) 파악
3.  **멈춤 유사도 (Pause)**: 문장 내 휴지기(Gap) 횟수 + 위치 F1 블렌딩 (`domain/scoring/pause_scoring.py`)
4.  **단어 리듬 점수 (Rhythm)**: 단어별 상대 발화 비중(Relative Duration) 비교 (`domain/scoring/rhythm_scoring.py`)
5.  **억양 유사도 (Prosody)**: DTW 정렬 후 **Pearson 상관계수 유사도 × 타이밍 벌점** Hybrid 산출 (`domain/scoring/prosody_scoring.py`)
6.  **종결 억양 (Boundary Tone)**: 문장 끝 F0 기울기(slope) 방향 비교 (`domain/scoring/boundary_scoring.py`)
7.  **역동성 (Dynamic Stress)**: 전체 발화의 RMS 변동 계수(CV) 비교 (`domain/scoring/dynamic_scoring.py`)
8.  **단어별 피치 컨투어 피드백 (Pitch Contour)**: 개별 단어의 F0 방향(rising/falling/flat) 판별 (`domain/scoring/pitch_contour.py`)

### 7단계: 최종 점수 (Weighted Total Score) 산출
설정파일(`config.yaml`)에 정의된 **SCORE_WEIGHTS (가중치 비율)** 값을 바탕으로 위의 점수들을 합산합니다.
이 총합 점수가 패스 임계점(`PASS_THRESHOLD`)을 넘겼는지에 따라 `PASS / FAIL` 여부를 `evaluate_result.json`으로 정리하여 응답합니다.
