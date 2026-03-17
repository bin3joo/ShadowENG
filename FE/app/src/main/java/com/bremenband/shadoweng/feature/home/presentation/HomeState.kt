package com.bremenband.shadoweng.feature.home.presentation

data class HomeUiState(
    val streak: StreakData? = null,
    val latestSession: LatestSession? = null,
    val medals: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class StreakData(
    val currentDay: Int,
    val weeklyStatus: List<Boolean>
)

data class LatestSession(
    val sessionId: Long,
    val thumbnailUrl: String,
    val progressRate: Int,
    val title: String = "How to Fly with TED",
    val completedCount: Int = 8,
    val totalCount: Int = 16
    // TODO: 백엔드 필드 추가 후 연결
)

sealed class HomeEvent {
    object ClickLatestSession : HomeEvent()
    object ClickMoreSessions : HomeEvent()
    object ClickRegister : HomeEvent()
    object ClickGame : HomeEvent()
}