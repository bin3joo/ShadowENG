# Pipe Change Log
 
## 2026-03-12

- `pipeline.py` 내의 중복 F0 정규화 및 빈 결과 dict 생성 로직을 헬퍼 함수로 통합했습니다.
- `pipeline.py`의 단어 매칭 검색 알고리즘을 선형 스캔($O(n^2)$)에서 `dict` 기반 룩업($O(n)$)으로 최적화했습니다.
- `reference_service.py`의 리소스 정리 로직을 `finally` 블록으로 통합하여 안정성을 높이고 코드 중복을 제거했습니다.
- `quality.py`와 `reference_service.py` 간의 오디오 메트릭 계산 중복 호출을 제거하고 결과를 재사용하도록 개선했습니다.
- `A1_WORDS` 상수 내 중복 항목 제거 및 번역 서비스 내 화자 분석 로직 임포트 재사용 등 전반적인 코드 클린업을 수행했습니다.
- 상세 변경 내역은 `docs/review/code_refactoring_optimization_2026-03-12.md`에 기록했습니다.

## 2026-03-11

- `generate-reference`의 내부 패딩은 유지하되 최종 `reference.json`, `full_audio.wav`, part 오디오의 시간 기준을 요청 구간 기준(A)으로 통일했습니다.
- manual caption 경로에서 padded clip 기준 timestamp 가 외부 응답으로 섞이던 문제를 줄이기 위해 word timestamp rebasing 로직을 추가했습니다.
- evaluate 경로의 `_align_user_words_to_ref()`를 연음/축약 canonical token sequence 기반으로 재구성했습니다.
- `gonna`, `wanna`, `gotta`, `lemme`, `gimme`, `I'm` 류 표현이 evaluate 정렬에서 더 자연스럽게 매칭되도록 `engine_utils` canonicalization 유틸을 추가했습니다.
- 관련 변경 배경과 검증 포인트를 `docs/review/timebase_and_alignment_2026-03-11.md`에 기록했습니다.
- 공개 `generate-reference` 응답에서 내부 수치처리/디버그 메타데이터를 제외하고, 백엔드 전달에 필요한 핵심 필드만 남기도록 응답 계약을 축소했습니다.
- `parts.word_timestamps`에서는 `speaker`, `score`를 제거했고, top-level 번역 상태는 `translation_status` 대신 `translation_success`로 정리했습니다.

## 2026-03-10

- active 문서와 archive 문서를 분리하는 기준을 정리했습니다.
- `docs/legacy/`를 archive 전용 폴더로 확정했습니다.
- 기록성 문서를 `docs/` 하위로 이동해 정리했습니다.
- `main.py` 추가 분리를 위한 책임 정의 문서를 작성했습니다.
- FastAPI 앱 구성을 `api/`, `services/`, `integrations/` 패키지로 분리했습니다.
- reference payload helper를 `services/reference_payload.py`로 이동했습니다.
- 핵심 처리 로직을 `domain/processing` 패키지로 이동하기 시작했습니다.
- 루트 `main.py`는 최소 FastAPI 진입점(`pipe.main:app`)으로 유지했습니다.
- 루트 처리 모듈(`audio_processing.py`, `quality.py`, `text_processing.py` 등)은 제거하고 `domain/processing` 경로를 기준으로 정리했습니다.
- `HTTPException` 경로에서 임시 파일 정리가 누락될 수 있는 부분을 서비스 레이어에서 보완했습니다.

## 2026-03-09

- `generate-reference` 입력 계약을 `youtube_url`에서 `video_id`로 전환했습니다.
- `evaluate-audio` 응답에 `pass_fail`, `pass_threshold`를 추가했습니다.
- `main.py` 책임 일부를 `schemas.py`, `youtube_service.py`, `io_utils.py`, `reference_service.py`로 분리했습니다.
- `generate-reference` top-level 응답에 `pause_count`, `active_speech_sec`, `word_count`를 추가했습니다.
- short-part merge의 terminal punctuation 보호를 제어하는 설정을 추가했습니다.
- `engine.py`를 하위 호환 re-export hub로 정리했습니다.

## 2026-03-05 ~ 2026-03-06

- 채점 수식, pause 계산, speed penalty, boundary tone, dynamic stress 기준을 정리했습니다.
- JSON body 기반 `evaluate-audio` 요청 구조를 정비했습니다.
- 번역, pitch contour feedback, reduction detection, difficulty 분류 관련 개선을 반영했습니다.
