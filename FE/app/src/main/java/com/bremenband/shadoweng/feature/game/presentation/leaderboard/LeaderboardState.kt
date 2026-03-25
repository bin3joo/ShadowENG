package com.bremenband.shadoweng.feature.game.presentation.leaderboard

import com.bremenband.shadoweng.feature.game.domain.model.GameLeaderboard
import com.bremenband.shadoweng.feature.game.domain.model.Ranker

data class LeaderboardUiState(
    val tier: String = "",
    val promotionCount: Int = 0,
    val daysUntilReset: Int = 0,
    val hoursUntilReset: Int = 0,
    val minutesUntilReset: Int = 0,
    val topRankers: List<Ranker> = emptyList(),
    val nearbyRankers: List<Ranker> = emptyList(),
    val frozen: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class LeaderboardEvent {
    object ClickPlay : LeaderboardEvent()
}