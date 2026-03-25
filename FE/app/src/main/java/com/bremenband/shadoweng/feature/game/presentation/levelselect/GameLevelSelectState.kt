package com.bremenband.shadoweng.feature.game.presentation.levelselect

import com.bremenband.shadoweng.feature.game.domain.model.LevelStatus

data class GameLevelSelectUiState(
    val levels: List<LevelStatus> = emptyList(),
    val isRulesExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class GameLevelSelectEvent {
    data class SelectLevel(val level: Int) : GameLevelSelectEvent()
    object ToggleRules : GameLevelSelectEvent()
}
