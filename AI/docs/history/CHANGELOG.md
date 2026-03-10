# Pipe Change Log

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
