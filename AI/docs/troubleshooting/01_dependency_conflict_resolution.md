# AI 의존성 관리 및 트러블슈팅 가이드

본 문서는 StyleEcho AI 서버 개발 중 발생한 복잡한 의존성 충돌 사례와 이를 해결하기 위한 전략을 기록합니다.

---

## 🚨 핵심 요약 (Summary)

1. **PyTorch Ecosystem Hard-Lock**: `audio-separator`, `whisperx` 등이 `pip install` 시 `torch` 버전을 강제로 최신화(2.0.1 -> 2.10+)하여 CUDA 11.8 호환성이 깨지는 문제 발생.
2. **--no-deps 설치 전략**: 충돌을 일으키는 핵심 ML 패키지들은 `requirements_no_deps.txt`로 분리하여 의존성 없이 설치.
3. **Windows Cython 빌드 회피**: `av`, `onnx2torch` 등이 Windows에서 소스 빌드를 시도하며 실패하는 문제를 바이너리 휠 설치로 해결.
4. **Binary Execution 차단**: 보안 프로그램(안랩 등)의 `yt-dlp.exe` 차단 문제를 Python Native API 사용으로 회피.

---

## 1. 주요 충돌 내용 및 해결 방법

### 1-1. PyTorch 버전 강제 업그레이드 및 CUDA 충돌
- **문제**: `pip install audio-separator[gpu]` 실행 시 `pip`가 `torch>=2.3` 및 최신 `torchaudio`를 끌어옴. 이로 인해 CUDA 11.8 환경에서 `torch.cuda.is_available() == False`가 되거나, `torchaudio.list_audio_backends` 속성 누락으로 서버 시작 실패.
- **해결**: 
    - `requirements.txt`에 `--extra-index-url https://download.pytorch.org/whl/cu118` 추가.
    - 코어 패키지(`torch`, `torchaudio`, `torchvision`) 버전을 엄격히 고정(`==`).
    - 충돌 유발 패키지는 `requirements_no_deps.txt`로 이동.

### 1-2. CTranslate2 & CUDA DLL 미설치 에러
- **문제**: 최신 `ctranslate2`는 CUDA 12를 요구함. CUDA 11.8 환경에서 `cublas64_12.dll` 로드 실패 에러 발생.
- **해결**:
    - `ctranslate2==3.24.0`으로 다운그레이드.
    - 이에 맞춰 `faster-whisper==0.10.1`로 버전 조정 (의존성 매칭).

### 1-3. Windows 'av' (PyAV) 빌드 실패
- **문제**: `faster-whisper`와 VR 관련 패키지들이 `av`를 요구함. Windows 환경에서 Cython 컴파일러 부재로 소스 빌드가 실패하여 전체 설치가 중단됨.
- **해결**:
    - `pip install "av<13" --only-binary :all:` 명령어를 통해 컴파일 없이 바이너리 휠을 강제 사용하도록 유도.

### 1-4. 보안 프로그램의 yt-dlp.exe 차단
- **문제**: `subprocess.run(["yt-dlp", ...])` 방식은 별도 프로세스를 띄우므로 기업용 보안 프로그램(AhnLab 등)이 "위험한 다운로더 실행"으로 간주하여 차단함.
- **해결**:
    - `import yt_dlp`를 통해 파이썬 내부 라이브러리 API를 직접 호출하는 방식으로 로직 수정. (Native API 사용)

---

## 2. 권장 설치 순서 (Setup Flow)

환경을 새로 구축할 때는 반드시 아래 스크립트를 사용하십시오.

### Windows (Git Bash 기준)
```bash
# 1. 시스템 레벨 의존성 (FFmpeg) 설치
conda install -c conda-forge ffmpeg -y

# 2. PyTorch GPU 버전 사전 설치
pip install torch==2.0.1+cu118 torchvision==0.15.2+cu118 torchaudio==2.0.2+cu118 --index-url https://download.pytorch.org/whl/cu118

# 3. 기본 패키지 설치
pip install -r requirements.txt

# 4. 충돌 패키지 강제 설치 (의존성 검사 무시)
pip install -r requirements_no_deps.txt --no-deps
```

---

## 3. 관리 파일 구조

- `requirements.txt`: 안전한 일반 패키지 및 코어 런타임.
- `requirements_no_deps.txt`: 버전 락이 필요한 위험 패키지 리스트.
- `setup.bat` / `setup.sh`: 위 복잡한 설치 과정을 자동화한 스크립트.

---

## 4. 향후 주의사항
- 신규 오디오/ML 라이브러리 추가 시, 해당 패키지가 `numpy`나 `torch`를 요구하는지 항상 확인하십시오.
- `pip install` 후 `torch.cuda.is_available()`이 `False`로 변했다면 즉시 `setup.sh`를 재실행하여 환경을 복구하십시오.
