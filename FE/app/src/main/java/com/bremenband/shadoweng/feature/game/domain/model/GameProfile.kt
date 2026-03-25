package com.bremenband.shadoweng.feature.game.domain.model

data class GameProfile(
    val tier: String,
    val weeklyScore: Double,
    val frozen: Boolean
)