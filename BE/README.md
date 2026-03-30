# 잉무 백엔드 서버 Specification

## 1. 개요

* **목표:** Android 클라이언트의 요청을 처리하는 메인 REST API 서버. YouTube 영상 기반 학습 세션 관리, 음성 평가 요청 중계, 게임 플레이 기록, 사용자 인증/관리 기능을 제공합니다.
* **주요 역할:**
  * 게스트/카카오 소셜 로그인 및 JWT 토큰 발급·갱신
  * YouTube URL로부터 영상 정보 조회 및 학습 세션 생성
  * 학습자 음성 파일을 AI 서버로 중계하고 결과를 반환
  * Step 1~3 평가 결과를 Redis에 임시 저장하고, Step 4 완료 시 DB에 일괄 커밋
  * 게임(잉무를 꼬셔라) 라운드 진행, 점수 계산, 티어·리더보드 관리
  * 학습 완료 시 레포트 자동 생성 및 조회
* **비고:** Spring Boot 기반 Java 서버이며, AI Worker(Python/FastAPI)와 HTTP로 통신합니다.

## 2. 개발 환경 및 공통 설정

* **언어:** Java 21
* **서버 프레임워크:** Spring Boot 3.5.11
* **빌드 도구:** Gradle
* **데이터베이스:** PostgreSQL (JPA/Hibernate, DDL auto: update)
* **캐시:** Redis (Step별 평가 임시 저장, JWT Refresh Token 저장)
* **파일 스토리지:** AWS S3 (레퍼런스 오디오 특징 벡터, 메타데이터)
* **API Base Path:** `/api/v1/app`
* **API 문서:** Swagger UI (`/swagger-ui/index.html`)

## 3. 사용할 라이브러리 (Tech Stack)

* **Web / Validation:** `spring-boot-starter-web`, `spring-boot-starter-validation`
* **ORM:** `spring-boot-starter-data-jpa`, `postgresql`
* **Cache:** `spring-boot-starter-data-redis`
* **Security / Auth:** `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `jjwt-api 0.12.5`
* **HTTP Client:** `spring-cloud-starter-openfeign` (YouTube API), `RestClient` (Python AI 서버)
* **Cloud:** `software.amazon.awssdk:s3` (bom 2.31.16)
* **API 문서:** `springdoc-openapi-starter-webmvc-ui 2.8.16`
* **유틸:** `lombok`, `spring-dotenv 4.0.0` (`.env` 파일 지원)
* **가상 스레드:** Java 21 Virtual Threads 활성화 (`spring.threads.virtual.enabled: true`)

## 4. 기능 상세 명세

### 4.1. 인터페이스 (API)

* **프로토콜:** HTTP REST
* **응답 포맷:** `application/json`
* **Base Path:** `/api/v1/app`
* **인증 방식:** Bearer JWT (Access Token) — 로그인/토큰 갱신 엔드포인트 제외 모든 요청에 필요

### 4.2. 인증 (Auth)

* **역할:** 게스트 로그인, 카카오 소셜 로그인, JWT 토큰 재발급, 로그아웃 처리
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/auth/login/guest` | 디바이스 ID(UUID)로 게스트 계정 생성 또는 기존 계정 로그인 |
| `POST` | `/auth/login/kakao` | 카카오 인가 코드로 로그인 (미구현) |
| `POST` | `/auth/refresh` | Refresh Token 검증 후 Access/Refresh Token 재발급 |
| `POST` | `/auth/logout` | 서버의 Refresh Token 삭제 |

* **토큰 정책:**
  * Access Token: 1시간 (3,600,000ms)
  * Refresh Token: 7일 Sliding Window (최대 30일)
  * Refresh Token은 Redis에 저장

### 4.3. 사용자 (User)

* **역할:** 메인 화면 데이터 조회, 대시보드, 사용자 정보 조회·수정
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/users/main` | 이번 주 학습 요일, 최근 세션, 게임 요약 조회 |
| `GET` | `/users/dashboard` | 연속 출석일, 총 출석일, 총 학습 문장 수, 총 학습 시간, 날짜 목록 조회 |
| `GET` | `/users/me` | 인증된 사용자 상세 정보 조회 |
| `PATCH` | `/users/nickname` | 닉네임 변경 |

### 4.4. YouTube

* **역할:** YouTube URL을 분석하여 영상 ID, 제목, 썸네일, Embed URL 반환
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/youtube?url=` | YouTube URL로 영상 정보 조회 및 Embed URL 반환 |

* **특징:** YouTube Data API v3 호출 (`googleapis.com/youtube/v3`)

### 4.5. 학습 세션 (Study Session)

* **역할:** 학습 세션 CRUD, 문장별 학습 데이터 조회, 복습 모드 제어, 재학습 세션 생성
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/study-sessions` | 사용자의 전체 세션 조회 (ACTIVE/COMPLETED 분류) |
| `POST` | `/study-sessions` | embedUrl + 시간 구간으로 세션 생성, 전사 문장 목록 반환 |
| `GET` | `/study-sessions/recent` | 가장 최근 학습 세션 조회 |
| `GET` | `/study-sessions/{sessionId}` | 단일 세션 상세 조회 (영상 정보 + 문장 목록) |
| `DELETE` | `/study-sessions/{sessionId}` | 세션 Soft Delete (상태 → DELETED) |
| `POST` | `/study-sessions/{sessionId}/re-learn` | 완료된 세션 복사해 재학습 세션 생성 |
| `PATCH` | `/study-sessions/{sessionId}/review` | 복습 모드 활성화 (isReviewing → true) |
| `PATCH` | `/study-sessions/{sessionId}/name` | 세션 이름 변경 |
| `GET` | `/study-sessions/{sessionId}/sentences/{sentenceId}` | 문장 학습 데이터 조회 (step에 따라 원문/마스킹 반환) |

* **세션 생성 흐름:**
  1. embedUrl + 시간 구간 수신
  2. AI 서버 `/api/v1/generate-reference` 호출
  3. 전사된 문장(Part) 목록과 레퍼런스 데이터를 DB 및 S3에 저장
  4. 세션 + 문장 목록 반환

### 4.6. 음성 평가 (Evaluation)

* **역할:** 학습자 음성 파일을 AI 서버로 중계하여 발음·억양·리듬 분석 결과 반환
* **Endpoint:**

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/study-sessions/{sessionId}/evaluations` | Multipart 음성 파일 수신 후 평가 결과 반환 |

* **Query Parameters:** `sentenceId` (Long), `step` (1~4)
* **Request Body:** `multipart/form-data` — `file` 필드 (wav, m4a 등)

* **처리 단계:**
  1. 세션 소유권 및 문장 귀속 검증
  2. 복습 모드 및 이전 Step 완료 여부 검증 (Step 2 이상)
  3. 음성 파일 최소 크기 검증 (5,000 bytes)
  4. 음성 파일 Base64 인코딩
  5. S3에서 레퍼런스 F0/RMS 특징 벡터 및 단어 타임스탬프 로드
  6. Python AI 서버 `POST /api/v1/evaluate-audio` 호출 (최대 35초)
  7. 평가 결과를 Redis에 임시 저장 (TTL 24시간, Key: `pending_eval:{sessionId}:{sentenceId}:{step}`)
  8. Step 4 완료 시: Redis에서 Step 1~4 전부 로드 → DB 일괄 커밋 → Redis 삭제
  9. Step 4 + 복습 모드일 경우: DB 커밋 전 직전 사이클의 Step 4 점수 조회 (이전 점수 비교용)
  10. `EvaluationResponse` 반환

* **응답 포함 정보:**
  * `step`, `sentenceId`, `startSec`, `endSec`, `durationSec`
  * `userTranscription` — STT 인식 결과
  * `scores` — 7대 점수 (totalScore, wordAccuracy, prosodyAndStress, wordRhythmScore, boundaryToneScore, dynamicStressScore, speedSimilarity, pauseSimilarity)
  * `details` — 단어별 피드백, 종결 억양 피드백, 강세 피드백
  * `previousScores` — 복습 모드 시 직전 사이클 점수

### 4.7. 게임 (Game — 잉무를 꼬셔라)

* **역할:** 일일 게임 문장 조회, 라운드별 발음 평가, 점수 저장, 티어·리더보드 관리
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/game/today` | 레벨별 해금 상태 및 오늘의 최고 기록 조회 |
| `GET` | `/game/levels/{level}/rounds/{round}` | 라운드별 문장 조회 (round 1: 전체, round 2: 마스킹, round 3: null) |
| `POST` | `/game/levels/{level}/rounds/{round}/evaluate` | 라운드 음성 평가 (총점 70점 미만 또는 round 3 완료 시 게임 종료) |
| `GET` | `/game/leaderboard` | 사용자 티어 내 순위 조회 (freeze 상태 접근 불가) |
| `GET` | `/game/profile` | 내 게임 프로필 조회 (티어, 주간 점수, freeze 여부) |

* **레벨 해금 조건:**
  * Level 2: Level 1 `daily_best_records`의 hearts ≥ 1
  * Level 3: Level 2 `daily_best_records`의 hearts ≥ 1

* **점수 계산:**
  * `avg_total = wordAccuracy×0.5 + wordRhythm×0.3 + dynamicStress×0.2`
  * `final_score = avg_total × (1 + hearts×0.1) × 레벨배수` (level1: ×1.0, level2: ×1.5, level3: ×2.0)

* **티어 시스템:**
  * BRONZE → SILVER → GOLD → PLATINUM → DIAMOND → RUBY → CHALLENGER
  * 매주 월요일 주간 점수 기반 티어 승급/강등 (스케줄러)
  * 3주 연속 미플레이: 1단계 강등
  * 4주 이상 연속 미플레이: `frozen = true` (강등 없음, 리더보드 접근 불가)

* **스케줄러:**
  * 매일 자정: `daily_game_sentences`에 레벨별 문장 배정 (`DailyGameSentenceSeeder`)
  * 매주 월요일: 주간 점수 기반 티어 갱신 및 미플레이 카운트 처리 (`GameScheduler`)

### 4.8. 리포트 (Report)

* **역할:** 학습 세션 완료 후 평균 점수 및 취약 문장 분석 레포트 제공
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/study-sessions/{sessionId}/reports` | 특정 세션의 레포트 목록 최신순 조회 |
| `GET` | `/study-sessions/{sessionId}/reports/{reportId}` | 특정 레포트 단건 조회 |
| `GET` | `/reports` | 내 전체 레포트 목록 최신순 조회 |
| `GET` | `/reports/daily` | 일자별 학습 데이터 조회 |

* **특징:** 세션 내 모든 문장의 Step 4 완료 시 레포트 자동 생성 (`StudyReportFacade`)

### 4.9. 북마크 (Bookmark)

* **역할:** 학습 문장 북마크 추가/해제 및 북마크 목록 조회
* **Endpoints:**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/bookmarks` | 사용자 북마크 목록 조회 |
| `PATCH` | `/bookmarks` | 북마크 추가 또는 해제 (토글) |

## 5. 데이터 흐름 (음성 평가 파이프라인)

1. **Request:** Android 클라이언트가 `POST /study-sessions/{sessionId}/evaluations` 로 Multipart 음성 파일 전송
2. **Process (BE):**
   * 세션/문장 소유권 검증
   * 음성 파일 Base64 인코딩
   * S3에서 레퍼런스 특징 데이터(F0/RMS, 단어 타임스탬프) 로드
   * Python AI 서버 `POST /api/v1/evaluate-audio` 호출
   * 결과를 Redis에 임시 저장 (Step 1~3) 또는 DB 커밋 (Step 4)
3. **Process (AI Server):**
   * WhisperX STT + 7대 채점 지표 분석
   * 점수 + 피드백 JSON 반환
4. **Response:** `EvaluationResponse` (점수, 피드백, STT 결과) 반환

```
Android
  │ POST /study-sessions/{id}/evaluations (Multipart)
  ▼
BE (Spring Boot)
  │ 검증 → Base64 인코딩 → S3 레퍼런스 로드
  │ POST /api/v1/evaluate-audio (JSON)
  ▼
AI Server (Python/FastAPI)
  │ STT + 7대 채점 분석
  │ 점수 + 피드백 JSON 반환
  ▼
BE
  │ Redis 임시 저장 (Step 1~3)
  │ DB 일괄 커밋 (Step 4)
  │ EvaluationResponse 반환
  ▼
Android
```

---

## 5.1. 실행 방법 (개발)

* **JDK 설치:** Java 21 이상 필요
* **환경 변수 설정:** `BE/` 루트에 `.env` 파일 생성 (아래 Quick Start 참고)
* **서버 실행:**
  ```bash
  cd S14P21A306/BE
  ./gradlew bootRun
  ```
* **Docker Compose 실행 (PostgreSQL + Redis 포함):**
  ```bash
  cd S14P21A306/Infra
  docker-compose up -d
  ```

## 6. Quick Start (Usage)

### 6.1. 사전 요구사항

* **Java 21** (JDK)
* **Gradle** (Wrapper 포함이므로 별도 설치 불필요)
* **Docker & Docker Compose** — 로컬 PostgreSQL/Redis 실행용
* **AWS 계정** — S3 버킷 접근용

### 6.2. 리포지터리 클론

```bash
git clone <REPOSITORY_URL>
cd S14P21A306/BE
```

### 6.3. 환경 변수 설정 (`.env`)

프로젝트 루트(`BE/`)에 `.env` 파일을 생성합니다. (`.gitignore`에 포함되어 커밋되지 않습니다.)

```bash
# BE/.env
POSTGRES_USER=shadoweng
POSTGRES_PASSWORD=<DB 비밀번호>
REDIS_PWD=<Redis 비밀번호>
YOUTUBE_API_KEY=<YouTube Data API v3 Key>
PYTHON_API_URL=<AI 서버 URL (예: http://localhost:8000)>
```

`application-secret.yaml` 파일도 별도로 필요합니다:

```yaml
# BE/src/main/resources/application-secret.yaml
spring:
  datasource:
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

jwt:
  secret: <최소 32자 이상의 JWT 시크릿 키>

aws:
  s3:
    bucket: <S3 버킷명>
    region: ap-northeast-2
    access-key: <AWS Access Key>
    secret-key: <AWS Secret Key>

python:
  api:
    base-url: ${PYTHON_API_URL}

cors:
  allowed-origins: http://localhost:3000
```

| 변수 | 용도 | 필수 여부 |
|------|------|----------|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | PostgreSQL 접속 | 필수 |
| `REDIS_PWD` | Redis 인증 | 선택 |
| `YOUTUBE_API_KEY` | YouTube Data API v3 | 필수 |
| `PYTHON_API_URL` | AI 서버 URL | 필수 |
| `jwt.secret` | JWT 서명 키 (32자 이상) | 필수 |
| `aws.s3.*` | S3 레퍼런스 데이터 접근 | 필수 |

### 6.4. 데이터베이스 실행 (Docker)

```bash
cd S14P21A306/Infra
docker-compose up -d postgres-container redis-container
```

### 6.5. 서버 실행

```bash
cd S14P21A306/BE
./gradlew bootRun
```

서버 기동 후 `http://localhost:8080/api/v1/app/swagger-ui/index.html` 에서 API 문서를 확인할 수 있습니다.

### 6.6. 초기 데이터 적재

서버 최초 실행 시 `src/main/resources/data.sql` 이 자동으로 실행됩니다.
게임 문장(`game_sentences`) 및 기타 더미 데이터가 포함되어 있습니다.

---

## 7. 포팅/배포 참고 (Porting Guide)

### 7.1. 실행 환경

* **Java:** 21 이상
* **Framework:** Spring Boot 3.5.11 (Gradle)
* **DB:** PostgreSQL 14 이상
* **Cache:** Redis 7 이상
* **JVM 설정:** Virtual Threads 사용으로 높은 동시성 처리 가능 (Java 21 필수)

### 7.2. 설정 파일

* **`application.yaml`**
  * 서버 포트, DB 호스트/포트, Redis 호스트, CORS, JWT 만료시간, YouTube API Base URL 등 공통 설정
  * 환경변수 `DB_HOST`, `REDIS_HOST`, `CORS_ALLOWED_ORIGINS` 으로 override 가능

* **`application-secret.yaml`**
  * DB 인증정보, JWT 시크릿, AWS S3 자격증명, Python API URL 등 민감 정보
  * `.gitignore` 처리 필요 — 배포 환경에서는 환경변수 또는 Secret Manager 사용 권장

### 7.3. 외부 서비스 / 토큰

* **YouTube Data API v3 Key**
  * `GET /youtube` 영상 정보 조회 시 사용
  * Google Cloud Console에서 발급

* **AWS S3**
  * 레퍼런스 오디오 특징 벡터(`features.json`)와 메타데이터 저장 및 조회
  * AI 서버가 생성한 데이터를 S3에 업로드한 후 BE가 URL로 접근

* **Python AI 서버**
  * `POST /api/v1/evaluate-audio` — 음성 평가
  * `POST /api/v1/generate-reference` — 레퍼런스 생성 (학습 세션 생성 시)
  * 응답 최대 35초 소요 — Virtual Threads로 처리 (DB 커넥션 점유 방지를 위해 트랜잭션 외부에서 호출)

### 7.4. 배포 구성 (현재)

* **CI/CD:** GitLab `develop` 브랜치 push → Jenkins Webhook → `docker-compose down && up`
* **인프라:** AWS EC2 (ap-northeast-2) 위 Docker Compose

```
GitLab (develop)
    │ push
    ▼
Jenkins Pipeline
    │ docker-compose build & up
    ▼
EC2 Docker Containers
  ├ SpringBoot  (host:8080)
  ├ PostgreSQL  (host:5432)
  └ Redis       (host:6379)
    │ HTTP (PYTHON_API_URL)
    ▼
AI Server (ngrok → 로컬 GPU 머신:8000)
```

### 7.5. 에러 코드 체계

| 범위 | 영역 |
|------|------|
| 1000~1009 | 학습/입력 관련 오류 |
| 2000~2008 | DB 조회 실패 |
| 3000~3002 | 인증/토큰 오류 |
| 4000~4003 | 권한 오류 (게임 포함) |
| 5000 | 미구현 기능 |
| 9000~9999 | 시스템/외부 API 오류 |

모든 에러는 `GlobalExceptionHandler` 에서 통일된 `ApiResponse` 포맷으로 반환됩니다.
