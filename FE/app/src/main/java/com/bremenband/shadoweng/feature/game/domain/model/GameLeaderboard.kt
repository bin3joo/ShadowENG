package com.bremenband.shadoweng.feature.game.domain.model

data class GameLeaderboard(
    val tier: String,
    val promotionCount: Int,
    val daysUntilReset: Int,
    val hoursUntilReset: Int,
    val minutesUntilReset: Int,
    val topRankers: List<Ranker>,
    val nearbyRankers: List<Ranker>
)

data class Ranker(
    val rank: Int,
    val nickname: String,
    val weeklyScore: Double,
    val isMe: Boolean
)