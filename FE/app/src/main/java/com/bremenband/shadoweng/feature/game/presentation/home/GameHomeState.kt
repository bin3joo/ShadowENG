package com.bremenband.shadoweng.feature.game.presentation.home

import com.bremenband.shadoweng.feature.game.domain.model.LevelStatus

data class GameHomeUiState(
    val levels: List<LevelStatus> = emptyList(),
    val profile: ProfileInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ProfileInfo(
    val tier: String,
    val weeklyScore: Double,
    val frozen: Boolean
)

sealed class GameHomeEvent {
    object ClickPlay : GameHomeEvent()
    object ClickLeaderboard : GameHomeEvent()
}