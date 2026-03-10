# StyleEcho 코드 리뷰

> 날짜: 2026-03-10  
> 대상: `pipe/` 문서 정리 및 현재 코드 기준 미해결 개선 포인트 재정리  
> 기준: `main.py`, `pipeline.py`, `schemas.py`, `config.py`, `README.md` 현재 구현 기준

---

## 0. 요약

기존 `UX_improvements.md`, `AI_analysis.md`, `api-spec.md` 는
개발 과정의 변경 이력, 이미 반영된 기능, 삭제된 기능, 과거 기준 설명이 혼재되어 있었습니다.

이번 정리에서는 다음 원칙을 적용했습니다.

- 이미 코드에 반영된 내용은 별도 개선 과제로 유지하지 않음
- 삭제된 기능과 과거 변경 이력은 active 문서에서 제거
- 현재 시점에 **실제로 남아 있는 개선 포인트만** `code_review` 로 재구성
- 기존 문서는 `docs/legacy/` 폴더로 이동해 아카이브로 정리

---

## 1. 현재 문서 체계 정리 기준

### 1.1. Active 문서

- `README.md`
  - 현재 시스템 개요
  - 실행 방법
  - 설정 파일
  - 디렉토리 구조
- `docs/review/`
  - 코드 리뷰 및 현재 개선 과제 문서 관리 위치
- `docs/history/CHANGELOG.md`
  - 날짜 기반 변경 이력 문서
- `docs/architecture/main_refactor_plan.md`
  - `main.py` 추가 분리 기준 문서

### 1.2. Legacy 문서

- `docs/legacy/UX_improvements_2026-03-06.md`
- `docs/legacy/AI_analysis_2026-03-06.md`
- `docs/legacy/api-spec_2026-03-09.md`

이 문서들은 **개발 기록 보존용** 이며,
현재 active 스펙/구현의 단일 기준 문서로 사용하지 않습니다.

---

## 2. 현재 기준 미해결 개선 포인트

### IMP-01. 예외 계층화가 아직 제한적임

현재 일부 경로는 여전히 `except Exception` 중심입니다.

- 입력 오류
- 외부 서비스 오류
- 모델 로드 오류
- 번역 실패
- 다운로드 실패

가 충분히 구분되지 않습니다.

**권장 방향**

- `ReferenceDownloadError`
- `CaptionFetchError`
- `EvaluationInputError`
- `TranslationError`
- `DiarizationLoadError`

같은 도메인 예외를 정의하고 API 레이어에서 일관된 응답으로 변환하는 것이 좋습니다.

---

### IMP-02. 서비스 / 도메인 예외 경계는 더 정리 가능

현재 FastAPI 앱은 루트 `pipe.main:app` 최소 진입점을 유지하고,
실제 앱 구성은 `api/`, `services/`, `integrations/`, `domain/processing` 패키지로 분리되었습니다.

다만 서비스 레이어는 여전히 `HTTPException` 과 일반 예외 변환을 직접 포함하고 있어,
도메인 예외 계층과 API 레이어 변환 책임을 더 분리할 여지가 있습니다.

**권장 방향**

- service 레이어는 도메인 의미의 예외를 raise
- API 레이어는 HTTP 응답으로 변환
- 외부 연동 실패와 입력 실패를 구분하는 예외 타입 정리

---

### IMP-03. 번역은 순차 처리 구조라 지연 가능성이 있음

현재 reference 생성 시:

- 전체 스크립트 번역
- part 별 문장 번역

이 순차적으로 수행됩니다.

**영향**

- part 수가 많은 reference 에서 응답 지연 가능

**권장 방향**

- translation cache
- batch 처리
- 설정 기반 번역 off 옵션

---

### IMP-04. 저볼륨 / 무음 UX는 더 개선 가능

현재 무음 또는 인식 실패 처리는 존재하지만,
사전에 사용자에게 품질 저하 원인을 더 구체적으로 안내하는 UX는 제한적입니다.

**권장 방향**

- RMS 기반 저볼륨 사전 감지
- background noise 과다 경고 메시지 강화
- 녹음 재시도 가이드 메시지 표준화

---

### IMP-05. 음소 수준 발음 피드백은 아직 없음

현재 평가는 단어/억양/리듬 수준까지 제공되지만,
음소 단위 발음 오류는 직접 설명하지 않습니다.

**가능한 방향**

- `cmudict` 기반 경량 발음 설명
- `Wav2Vec2` / `HuBERT` 기반 phoneme error detection

**기대 효과**

- 같은 단어를 잘못 발음했지만 STT 상으로는 동일하게 인식되는 케이스 보완

---

### IMP-06. 진행도 비교 / 세션 간 비교 API는 없음

현재는 단일 평가 결과 반환만 지원합니다.

**후보 기능**

- 과거 점수 대비 delta 계산
- 향상/악화 단어 목록 반환

**예시 엔드포인트**

- `POST /api/v1/compare-progress`

이 기능은 Python 단독보다는 상위 서버와의 계약 설계가 먼저 필요합니다.

---
### IMP-07. YouTube 문장 검색 API는 아직 없음

현재는 `video_id + 구간` 입력만 받습니다.
문장 검색 기반 구간 추천 기능은 아직 구현되어 있지 않습니다.

**후보 기능**

- `POST /api/v1/search-segment`
- 자막 텍스트 검색 → 후보 segment 반환

**폐기**

- 문장 검색으로 구간을 찾는 방식은 제공하지 않을 예정

---

### IMP-08. reference feature 캐싱 전략은 아직 없음

현재 reference 생성 결과는 응답 payload 로 반환되지만,
장기 캐시/DB 저장 전략은 명시적으로 포함되어 있지 않습니다.

**영향**

- 동일 reference 재사용 시 백엔드 저장 전략이 별도로 필요

**권장 방향**

- reference hash 또는 `video_id + start_sec + end_sec` 기반 캐시 키 설계
- feature / parts / quality metadata 저장 포맷 표준화

---

### IMP-09. 처리 단계 스트리밍은 미구현

현재 API는 요청 완료 후 결과를 한 번에 반환합니다.

**후보 기능**

- SSE
- WebSocket
- polling 기반 job status

**적합한 시나리오**

- 긴 reference 생성
- 대형 모델 초기 로드
- 번역/diarization 포함 처리

---

### IMP-10. 학습 개인화 로직은 외부 의존이 큼

개인화 난이도 조절, spaced repetition, 북마크 추천 등은
현재 Python Worker의 직접 책임으로 구현되어 있지 않습니다.

**후보 기능**

- SM-2 기반 복습 주기 계산
- score 기반 bookmark 추천 단어 반환
- 누적 통계 기반 사용자 약점 분석

이 항목들은 상위 서버/DB와의 데이터 계약이 먼저 필요합니다.

---

## 3. 문서 정리 결과

### 제거 대상 성격

기존 문서들에는 아래 성격의 정보가 섞여 있었습니다.

- 이미 구현 완료된 기능 목록
- "신규 구현", "수정됨" 같은 시점 의존 표현
- 삭제된 엔드포인트/기능 기록
- 과거 파일 구조 설명
- 현재 코드와 불일치하는 예전 계약 설명

이 정보들은 active 문서에 계속 남아 있으면,
오히려 현재 기준 문서의 신뢰도를 떨어뜨릴 수 있습니다.

### 정리 원칙

- 현재 구현 설명은 `README.md` 중심
- 코드 리뷰와 구조 검토 문서는 `docs/review/`
- 변경 이력은 `docs/history/`
- 구조 분리 계획 문서는 `docs/architecture/`
- 과거 제안/변경 로그는 `docs/legacy/`

---

## 4. 후속 권장 작업

- `README.md` 와 실제 폴더 구조 동기화 상태를 계속 유지
- 필요 시 API 계약은 추후 `openapi` 또는 간결한 `api-reference.md` 로 재작성
- `docs/review/`, `docs/tuning/`, `docs/legacy/` 역할 구분 유지
- 변경 이력은 `docs/history/CHANGELOG.md` 중심으로 누적

---

## 5. 결론

현재 `pipe/` 는 기능적으로 이미 상당 부분 정리되어 있으며,
이제 문서도 아래처럼 역할을 분리하는 것이 적합합니다.

- **README:** 현재 구조와 사용법
- **docs/review:** 남은 개선 포인트와 리뷰 결과
- **docs/history:** 변경 이력과 날짜 기준 기록
- **docs/architecture:** 구조 개편 계획과 책임 분리 기준
- **docs/legacy:** 과거 분석/제안/이력 문서 보관

## 설계 담당자 제안 ##

- 기능 변경 이력과 시간 정보는 새로운 문서로 작성하여 관리하는 것이 좋을 것 같음
- legacy 폴더는 archive 용도로만 사용하는 것이 좋을 것 같음
- legacy 이외 README에 해당하지 않는 기록들은 (code_review 등) docs 폴더에 별도 문서로 관리하는 것이 좋을 것 같음

- 문서 작업 이외에 main.py의 모듈 분리 작업 후 기능별 폴더 관리 구조로 변경하는 것이 좋을 것 같음. 내부 기능을 쓰는 작업과 외부 API를 사용하는 작업을 구분하는 것이 좋을 것 같음
- 폴더 구조 변경 시 README에 변경된 구조를 반드시 명시
- 그 외의 작업에는 주기적인 코드 리팩토링 필요 여부를 파악하여 효율성과 버그 수정을 위한 작업이 필요할 것 같음

- 앞으로 추가할 기능: 현재 존재하는 번역 모델을 로드하여 한글 번역하는 로직을 LLM API를 호출하여 전체 문장을 번역하고 문장 파트별로 번역결과를 나누는 작업으로 전환
- 주 내용으로는 전체 문장 번역 -> 학습시 중요도가 높은 문장을 추출 (자주쓰는 표현 또는 숙어 등) -> 강조 표현으로 backend에 전송하여 제공
- 문장 나눔을 LLM으로 대체하여 멀티턴과 자연스러운 대화 흐름을 구현할 수 있도록 개선가능한지 검토