package com.bremenband.shadoweng.feature.content.presentation.loading

enum class ContentAnalysisStep(val label: String) {
    STT("음성을 텍스트로 변환하고 있어요"),
    SENTENCE_SPLIT("따라 말할 문장을 준비하고 있어요"),
    READY("곧 이 장면으로 쉐도잉을 시작할 수 있어요")
}

enum class ContentStepStatus { PENDING, IN_PROGRESS, DONE }

data class ContentAnalysisStepState(
    val step: ContentAnalysisStep,
    val status: ContentStepStatus = ContentStepStatus.PENDING
)

data class LoadingTip(
    val emoji: String,
    val label: String,
    val content: String
)

val loadingTips = listOf(
    LoadingTip("🐦", "잉무의 한 마디", "영어 대화 속도는 보통 분당 약 120~150단어 정도예요. 처음엔 속도보다 흐름에 익숙해지는 게 더 중요해요!"),
    LoadingTip("💡", "알고 계셨나요?", "완벽한 문장보다 끝까지 말하는 습관이 말하기에 더 도움 돼요."),
    LoadingTip("📊", "영어 정보", "발표처럼 또박또박 말할 때는 보통 분당 100~150단어 정도가 편안한 속도로 여겨져요."),
    LoadingTip("💡", "알고 계셨나요?", "짧은 문장을 여러 번 따라 말하는 연습이 부담이 덜해요."),
    LoadingTip("📊", "영어 정보", "오디오북이나 팟캐스트는 보통 분당 150~160단어 정도로 들리는 경우가 많아요."),
    LoadingTip("💡", "알고 계셨나요?", "뜻을 다 몰라도 먼저 소리와 리듬을 따라 해보세요!"),
    LoadingTip("📊", "영어 정보", "전 세계에서 영어를 쓰는 사람은 약 23억 명이에요. 잉무도 그 중 하나예요 🌍"),
    LoadingTip("💡", "알고 계셨나요?", "잘 외운 표현 하나가 어려운 단어 열 개보다 더 빨리 입에서 나와요."),
    LoadingTip("📊", "영어 정보", "영어 사용자 대부분은 원어민이 아니라 비원어민이에요. 우리가 다수예요!"),
    LoadingTip("💡", "알고 계셨나요?", "원어민처럼 완벽하게보다, 알아듣게 말하는 걸 먼저 목표로 해보세요."),
    LoadingTip("📊", "영어 정보", "전 세계 영어 대화의 약 4%만이 원어민끼리의 대화예요. 영어는 원래 다양한 억양 속에서 쓰여요!"),
    LoadingTip("💡", "알고 계셨나요?", "말문이 안 열릴 땐 긴 문장보다 짧은 문장 3개가 더 쉬워요."),
    LoadingTip("📊", "영어 정보", "영어는 전 세계에서 가장 널리 쓰이는 국제 공용어 중 하나예요."),
    LoadingTip("💡", "알고 계셨나요?", "입이 잘 안 풀리는 날엔 큰 소리보다 작은 소리로라도 계속 말해보세요."),
    LoadingTip("🐦", "잉무의 한 마디", "영어 말하기 불안은 많은 학습자가 겪는 어려움이에요. 잉무가 응원할게요! 🌟"),
    LoadingTip("💡", "알고 계셨나요?", "잘 못 말해도 괜찮아요. 멈추지 않고 이어가는 연습이 더 중요해요."),
    LoadingTip("📊", "영어 정보", "단어가 바로 떠오르지 않는 순간은 누구나 겪는 거예요. 너무 걱정 마세요!"),
    LoadingTip("💡", "알고 계셨나요?", "모르는 단어가 있으면 쉬운 말로 바꿔서라도 이어 말해보세요."),
    LoadingTip("📊", "영어 정보", "쉐도잉과 트래킹이 말하기 유창성 향상에 도움이 된다는 연구 결과도 있어요!"),
    LoadingTip("💡", "알고 계셨나요?", "쉐도잉은 해석보다 먼저 소리와 억양을 복사한다는 느낌으로 해보세요."),
    LoadingTip("📊", "영어 정보", "처음부터 빠르게 따라 하기보다, 천천히 정확하게 따라 하는 편이 더 좋아요."),
    LoadingTip("💡", "알고 계셨나요?", "같은 문장을 감정만 바꿔 여러 번 말하면 억양 연습에 좋아요!"),
    LoadingTip("🐦", "잉무의 한 마디", "잘 들리지 않는 문장은 자막보다 먼저 소리를 따라가 보세요."),
    LoadingTip("📊", "영어 정보", "쉐도잉은 꾸준히 할수록 효과가 좋아요. 오늘도 파이팅! 💪"),
    LoadingTip("🐦", "잉무의 한 마디", "처음 영어가 버겁게 느껴지는 건 이상한 일이 아니에요. 잉무도 처음엔 다 그랬어요 🐣"),
    LoadingTip("💡", "알고 계셨나요?", "잘 말하려고 애쓰기보다, 자주 말하는 쪽이 실력 만들기에 더 가까워요."),
    LoadingTip("🐦", "잉무의 한 마디", "잉무의 나이는 측정 불가예요. 영어 실력도 한계가 없어요! ✨"),
    LoadingTip("💡", "알고 계셨나요?", "오늘 연습한 문장이 내일 자연스럽게 입에서 나올 거예요."),
    LoadingTip("🐦", "잉무의 한 마디", "천천히 시작해도 괜찮아요. 꾸준함이 실력을 만들어요!"),
    LoadingTip("🐦", "잉무의 한 마디", "잉무가 열심히 분석하고 있어요. 조금만 기다려주세요! 🐦")
)

data class ContentLoadingUiState(
    val steps: List<ContentAnalysisStepState> = ContentAnalysisStep.entries.map { ContentAnalysisStepState(it) },
    val progress: Float = 0f,
    val isDone: Boolean = false,
    val currentTip: LoadingTip = loadingTips.first()
)