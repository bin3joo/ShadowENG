package com.bremenband.shadoweng.feature.game.domain.model

data class LevelStatus(
    val level: Int,
    val unlocked: Boolean,
    val todayBestHearts: Int?,
    val todayBestScore: Double?
)