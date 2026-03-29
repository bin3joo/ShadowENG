package com.bremenband.shadoweng.feature.home.presentation

data class HomeUiState(
    val streak: StreakData? = null,
    val latestSession: LatestSession? = null,
    val gameSectionData: GameSectionData? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class GameSectionData(
    val leagueTopScore: Int,
    val myScore: Int,
    val tier: String = ""  // 추가
)

data class StreakData(
    val currentDay: Int,
    val weeklyStatus: List<Boolean>,
    val longestStreak: Int = 0,
    val totalVisitedDays: Int = 0
)

data class LatestSession(
    val sessionId: Long,
    val thumbnailUrl: String,
    val progressRate: Int,
    val title: String = "",
    val completedSentences: Int = 0,
    val totalSentences: Int = 0
)

sealed class HomeEvent {
    object ClickLatestSession : HomeEvent()
    object ClickMoreSessions : HomeEvent()
    object ClickRegister : HomeEvent()
    object ClickGame : HomeEvent()
}