# 2. 레퍼런스 생성 내부 아키텍처 (Reference Generation Internals)

본 문서는 [`reference_service.py`](../../../services/reference_service.py) 패키지의 `generate_reference()` 함수가 내부적으로 어떤 파이프라인(순서와 병렬 처리 구간)을 거쳐 동작하는지 기술합니다.

## 🚀 아키텍처 핵심 요약 (Architecture Summary)

- **음성 인식 고정 (STT Stabilization)**: `Whisper STT`와 `Forced Alignment`는 VR 보컬 모델을 통과한 가공된 음원이 아닌, **항상 원본 오디오** 기준으로 수행하여 잡음 속에서도 원어민의 실제 숨결 타이밍을 놓치지 않도록 설계되었습니다.
- **다중 운율 소스 (Multi-Prosody Sourcing)**: 반면 운율(Prosody)에 해당하는 `F0`(Pitch)와 `RMS`(Intensity)는 설정에 따라 **원본 오디오**와 **별도의 VR 클린 오디오**에서 각각 동시에 추출한 뒤, 노이즈 왜곡이 덜한 최적의 소스를 `Source Gating` 룰셋으로 각기 판단하여 선택합니다.
- **LLM 네트워크 지연 원천 차단 (Zero-Wait LLM Strategy)**: 
  - 과거에는 "Prosody 추출(10초) → 품질 판독(2초) → LLM 번역(5초) ➔ 응답(총 17초)"의 직렬 구조였습니다.
  - 현재는 **"STT 결과물 문장 분할 직후 LLM 비동기 발사"** 라는 극단적인 병렬화 혁신을 도입했습니다. 즉 문장 구조만 나오면 바로 Gemini 번역을 돌려두고, 그 사이 서버는 `Prosody 추출, Part 분석, Reference 화자 품질 판독`이라는 **가장 무거운 연산들을 동시 수행**합니다. ➔ **체감 응답 속도 대폭 40~50% 감소.**

---

## 📅 내부 실행 로직 (Execution Flow)

1. **초기화 및 준비 단계**
    - `generator`, `audio/vr` 임시 파일 경로 세팅 및 요청 시간 자르기.
2. **Phase 1: 자막 및 오디오 병렬 다운로드**
    - `config.YOUTUBE_CAPTION_ENABLED`가 `true`(기본값)이면 YouTube Transcript API(수동 자막)를 병렬 조회합니다. `false`면 자막 조회를 건너뛰고 항상 Whisper STT Full Path로만 진행합니다.
    - YT-DLP로 오디오를 병렬 다운로드합니다.
3. **Phase 2: 보컬 분리 비동기 배치 (VR Optional)**
    - `vocal_remover.enabled`가 True이면 16kHz 다운로드 종결 즉시 `audio-separator`로 배경음 제거를 백그라운드로 밀어넣습니다.
4. **Phase 3: 음성 텍스트화 및 리파이닝 (STT & Refine)**
    - 원본 오디오에 대해 Caption Alignment (Fast Path) 또는 WhisperX STT (Full Path)를 거칩니다.
    - 양 극단 구간 밖의 단어를 잘라내고(Trim), 문장 내 특수문자를 정제(Sanitize)합니다.
5. **Phase 4: 문장 분할 및 동시 다발적 연산 포크 (Fork)** 🌟 (가장 중요한 부분)
    - 5-1. `split_into_sentences_with_timestamps(final_script, final_words)` 를 호출하여 기초 `sentence_data` 블록을 생성합니다.
    - 5-2. 생성 즉시 `deepcopy`를 뜬 후 `translate_reference_parts_with_gemini`를 ThreadPoolExecutor 백그라운드로 Submit 합니다. **이때부터 약 3~6초 간의 LLM 네트워크 유휴 시간 (I/O Bound) 이 발생합니다.**
6. **Phase 5: 프로소디 피처 완전 분석 (CPU/GPU Bound)** (위 5-2의 대기 시간 내에서 동시 수행)
    - 6-1. 오디오 신호 메트릭 평가(SNR 등) 및 디노이즈 모드 결정.
    - 6-2. 원본(`original`) 및 `VR` 버전에 대해 병렬로 `F0`, `RMS` 피처 추출.
    - 6-3. `Smart Cropping`, `Baseline Zeroing`, `Source Gating` 기술 적용.
    - 6-4. Reference 오디오 자체 품질 평가 (Reject / Risky 게이트 검열).
    - 만약 이 과정에서 스크립트 정렬 수준이 형편없다고 422 Reject 판정이 나면, 뒤에서 돌아가고 있는 Gemini Future에 `cancel()` 신호를 보내버리고 곧바로 차단합니다. (버림받은 비용 최소화)
7. **Phase 6: 조인 및 융합 (LLM Join & Data Merge)**
    - 위 6의 연산들이 먼저 끝난 뒤(혹은 비슷하게 끝남) `translation_future.result()`를 받아옵니다.
    - LLM이 똑똑하게 찢어진 두 파트를 한 파트로 자연스럽게 합쳤을(Merge) 경우, 여기에 맞춰서 Phase 5에서 구한 피처 배열과 Timestamp 배열을 재조립(`remerge`) 해줍니다.
8. **Phase 7: 안전 폐기 및 송신**
    - 오디오/VR 등의 파일 찌꺼기(`tempfile`) 삭제 트리거를 `BackgroundTasks`에 넘기고 JSON 짐 꾸려 `return`.

---

## 🚦 현재 동작 중인 병렬 구간 요약

현재 시스템이 성능 극대화를 위해 교차(Overlap) 실행하고 있는 4가지 코루틴/쓰레드 다발입니다:

1. `자막 다운로드 (Network)` ↔ `WAV 음원 추출 (Network + I/O)`
2. `audio-separator (GPU/CPU)` ↔ `WhisperX STT 및 정렬 (GPU/CPU)`
3. `Original Prosody (CPU/GPU)` ↔ `VR Prosody (CPU/GPU)`
4. **`Gemini LLM 번역 (Network)` ↔ `Prosody 분석 + 품질 판정 (CPU/GPU)`** 🌟 새로 도입된 최고의 가속 메커니즘.

## ⚠️ 동시성 고려 및 이슈 트래킹

- **`딥카피(deepcopy)` 엄수**: Gemini로 던져진 `sentence_data`는 다른 스레드에서 조작되고, 원본 스레드는 `sentence_data`에 오직 오디오 통계만 넣으며 놀아야 하므로, 리스트 포인터 복사 오류를 막기 위해 번역 쪽으로는 무조건 Deepcopy를 짭니다.
- **Best-Effort Cancel**: Python `concurrent.futures`의 `cancel()`은 아직 실행 전 큐에 있을 때만 완벽히 통제 가능하고 런닝 중이면 막을 도리가 없으므로 무거운 마음으로 그냥 넘어갑니다. (비용 청구 감수)
- **오디오 도메인 분리 규칙**: 무슨 일이 있어도 STT 시간축은 원본 오디오에 고정해야 합니다. VR 클린본으로 STT를 돌리면 묵음이 아닌 구간도 침묵으로 오해당해서 타임스탬프가 다 틀어집니다.
