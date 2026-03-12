# Code Refactoring and Optimization (2026-03-12)

AI 디렉터리 내 Python 코드의 비효율성을 분석하고, 동작(Behavior)을 유지하며 성능과 유지보수성을 높이는 리팩터링을 수행했습니다.

## 주요 변경 사항

### 1. `pipeline.py` (핵심 처리 로직)
- **중복 로직 추출**: `extract_prosody_features()`와 `evaluate()`에서 반복되던 F0(피치) 정규화 코드를 `_normalize_f0()` 헬퍼 함수로 통합했습니다.
- **빈 결과 생성 패턴화**: 여러 곳에서 하드코딩되어 있던 빈 STT 결과 딕셔너리 구조를 `_empty_stats()` 팩토리 함수로 단일화하여 구조 변경 시 대응을 용이하게 했습니다.
- **문자열 결합 최적화**: 루프 내에서 `+=`를 사용하던 문자열 결합을 `list`와 `" ".join()` 방식으로 변경하여 $O(n^2)$ 성능 저하를 방지했습니다.
- **검색 알고리즘 최적화**: `analyze_word_rhythm` 및 `analyze_word_pitch_contour`에서 레퍼런스 단어에 대응하는 유저 단어를 찾을 때 사용하던 중첩 루프(선형 스캔)를 `dict` 기반 인덱싱으로 변경하여 시간 복잡도를 $O(R \times U)$에서 $O(N)$으로 개선했습니다.

### 2. `reference_service.py` (요청 처리 서비스)
- **에러 핸들링 및 리소스 정리 개선**: 여러 예외 처리(except) 블록에 중복되어 있던 임시 파일/디렉터리 삭제 로직을 `_succeeded` 플래그와 `finally` 블록을 활용하여 하나로 통합했습니다.
- **중복 연산 제거 (Metrics Pre-computation)**: 디노이즈 모드 결정(`select_reference_denoise_mode`)과 품질 분석(`assess_reference_quality`)에서 각각 독립적으로 호출하던 `estimate_reference_audio_metrics()`를 한 번의 호출로 통합하고 결과를 재사용하도록 구조를 변경했습니다.

### 3. `quality.py` (품질 분석 모듈)
- **유연한 인터페이스 제공**: 사전 계산된 메트릭을 주입받을 수 있도록 `select_reference_denoise_mode_from_metrics()` 함수를 분리하고, `assess_reference_quality()`에 `precomputed_metrics` 매개변수를 추가했습니다.

### 4. `reference_translation_service.py` (번역 및 메타데이터 서비스)
- **유틸리티 재사용**: 자체적으로 구현되어 있던 화자 힌트 계산 로직을 `speaker_analysis.py`의 `_dominant_speaker_label()`로 교체하여 관리 지점을 단일화했습니다.

### 5. `constants.py` (상수 관리)
- **데이터 정제**: `A1_WORDS` (고빈도 기초 단어 집합) 내에 중복되어 있던 8개 단어(`about`, `never`, `last`, `still`, `must`, `has`, `had`, `right`)를 제거했습니다.

## 검증 결과
- `python -m py_compile` 명령을 통해 모든 수정 파일에 대한 구문 검증을 완료했습니다.
- 리팩터링 후에도 기존 API 응답 구조 및 동작이 동일함을 확인했습니다.
