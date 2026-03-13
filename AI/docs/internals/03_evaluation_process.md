# 3. 유저 발화 평가 파트 (Evaluation Process)

이 단계는 앱이나 웹 프론트엔드에서 넘어온 유저의 목소리와, 미리 계산되어 있던 레퍼런스(Reference JSON) 데이터를 1:1로 비교하여 다차원적인 점수를 산출하는 핵심 계층입니다. 
`services/evaluation_service.py` 모듈과 `pipeline.py`의 `evaluate()` 함수가 주도합니다.

## 시스템 흐름도 (Execution Flow)

### 1단계: 준비 (Initialization)
1. **유저 오디오 획득**: 사용자가 멀티파트 파일이나 URL 형태로 넘긴 오디오를 디스크에 저장(혹은 캐싱)합니다.
2. **레퍼런스 복원**: DB 또는 클라이언트로부터 전달받은 `final_script`(원문), `word_timestamps`(단어 좌표셋), `features/f0_array, rms_array`(기준점 피처)를 메모리에 Numpy 배열 등으로 재복원합니다.
3. **오디오 로드 및 STT용 정규화**: 유저 오디오를 `librosa.load`로 로드한 뒤, `peak_normalize_audio`를 적용한 **정규화 임시 WAV**를 생성합니다. 이 파일은 STT 전용으로 사용됩니다.

### 2단계: 유저 오디오 분석 (`extract_whisper_stats`)
**Peak 정규화된 임시 WAV**를 입력으로 WhisperX STT를 수행합니다. 볼륨이 작은 녹음에서도 Whisper의 환각(Hallucination)을 방지하고 인식률을 높입니다.
*   분석 결과로 유저가 실제로 내뱉은 **발화 내용 스트링(`text`)**, 전체 묵음 횟수(`pause_count`), 실제 유효 발성 시간(`active_speech_sec`), **단어 타임스탬프(`word_timestamps`)**가 도출됩니다.
*   STT 완료 후 임시 WAV는 즉시 삭제됩니다.

### 3단계: 논리적 1:1 매핑 (Alignment Sync)
레퍼런스가 요구하는 단어 개수 및 텍스트 구조와 유저가 말한 단어 구조가 100% 일치하지 않을 수 있습니다. 
따라서 `_align_user_words_to_ref` 함수를 사용하여 유저의 단어 타임스탬프를 **레퍼런스가 기대하는 단어 리스트와 매칭**시킵니다.
* 못 말한 단어는 누락(Missed) 처리하고 타임스탬프를 비웁니다.
* 억양이나 발음 점수는 이 매칭된 좌표를 바탕으로 1:1로 비교합니다.

### 4단계: 개별 스코어 추출 단계
7가지의 세분화된 스코어를 개별 로직으로 추출합니다 *(상세 수식은 `04_scoring_logic.md` 참고)*.
1.  **단어 정확도 (Word Accuracy)**: `jiwer` 패키지를 사용해 WER(Word Error Rate) 계산
2.  **속도 유사도 (Speed)**: `user_active_time`과 `ref_active_time`의 비율(Ratio) 파악
3.  **멈춤 유사도 (Pause)**: 문장 내 휴지기(Gap) 횟수 차이 기반 산출
4.  **피처 추출 (Extract User Prosody)**: **원본(Raw) 오디오** 배열에서 F0(피치)와 RMS(볼륨) 곡선을 도출합니다. 내부 Z-score 및 F0 정규화가 볼륨과 화자 차이를 자동 보정하므로 Peak 정규화는 적용하지 않습니다.
5.  **단어 리듬 점수 (Rhythm)**: 레퍼런스 발화 전체 길이 내 특정 단어가 점유한 시간 비율과 유저의 비율 차이를 비교 (e.g. `dragged`, `rushed`)
6.  **억양 유사도 (Prosody)**: 유저와 레퍼런스의 F0/RMS 피처 시계열 배열을 DTW 알고리즘을 사용해 왜곡 오차를 축소해가며 거리(Distance) 채점
7.  **종결 억양 및 역동성 (Boundary Tone / Dynamic Stress)**: 문장 끝의 높낮이 기울기 방향과 전체 발화의 목소리 톤 볼륨 역동성을 채점합니다.
8.  **단어별 피치 컨투어 피드백 (Pitch Contour)**: "here", "is" 처럼 개별로 매핑된 단어별로 F0의 시작Hz / 끝Hz 를 조사하여, 음정이 평탄한지(`flat`), 올라가는지(`rising`), 내려가는지(`falling`) 여부를 판별하여 교정 피드백(`lower_end`, `raise_end`, `good`)을 남깁니다.

### 5단계: 최종 점수 (Weighted Total Score) 산출
설정파일(`config.yaml`)에 정의된 **SCORE_WEIGHTS (가중치 비율)** 값을 바탕으로 위의 점수들을 합산합니다.
이 총합 점수가 패스 임계점(`PASS_THRESHOLD`)을 넘겼는지에 따라 `PASS / FAIL` 여부를 `evaluate_result.json`으로 정리하여 응답합니다.
