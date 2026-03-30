# ShadowENG - 영어 쉐도잉 학습 앱

> 좋아하는 영상으로 영어를 따라 말하고, AI가 발음을 분석해드려요.

---

## 📱 서비스 소개

ShadowENG은 유튜브 영상 구간을 기반으로 영어 쉐도잉 학습을 도와주는 Android 앱입니다.
원하는 영상 링크를 등록하면 자동으로 대본을 생성하고, 따라 말한 발음을 AI가 분석해 피드백을 제공합니다.

---

## ✨ 주요 기능

- **영상 등록** — 유튜브 링크 + 구간만 입력하면 자동으로 대본 생성
- **단계별 쉐도잉** — 4단계 난이도로 점진적 학습 (자막 → 부분 자막 → 자막 없음)
- **AI 발음 분석** — 단어 정확도, 억양, 리듬, 강세 등 상세 피드백
- **학습 리포트** — 세션별 종합 점수 및 어려운 문장 TOP3 하이라이트
- **학습 통계** — 캘린더 도장, 연속 출석, 따라 말한 문장 수 등
- **잉무 꼬시기 게임** — 발음 점수로 잉무를 꼬시는 리그 게임
- **리더보드** — 티어별 주간 점수 랭킹

---

## 🛠 기술 스택

| 분류 | 사용 기술 |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + UDF (Unidirectional Data Flow) |
| DI | Hilt |
| Network | Retrofit2 + OkHttp |
| Image | Coil |
| Video | ExoPlayer, Android-YouTube-Player |
| Navigation | Navigation Compose |

---

## 🗂 프로젝트 구조
```
app/
├── core/          # 공통 유틸, 네트워크, UI 컴포넌트
├── di/            # Hilt 의존성 주입 모듈
├── feature/       # 기능 단위 모듈
│   ├── auth/      # 로그인/회원가입
│   ├── home/      # 홈 화면
│   ├── study/     # 학습 세션, 하이라이트, 리포트
│   ├── mypage/    # 마이 쉐도잉
│   ├── stats/     # 학습 통계
│   ├── profile/   # 내 정보
│   └── game/      # 잉무 꼬시기 게임
├── navigation/    # NavGraph, NavRoutes
└── MainScreen.kt
```

---

## 🚀 실행 방법

1. 레포지토리 클론
```bash
git clone https://lab.ssafy.com/s14-ai-speech-sub1/S14P21A306.git
```

2. Android Studio에서 프로젝트 열기
   - `File > Open > android/` 폴더 선택

3. `build.gradle(app)`에 서버 주소가 설정되어 있습니다.
```kotlin
buildConfigField("String", "BASE_URL", "\"https://your-server-url.com\"")
```
배포 환경에 맞게 URL을 변경 후 빌드하세요.

4. 빌드 및 실행
   - `Run > Run 'app'` 또는 `Shift + F10`

---

## 📋 개발 환경

- Android Studio Ladybug 이상
- minSdk 26 (Android 8.0)
- targetSdk 35
- Kotlin 1.9+

---