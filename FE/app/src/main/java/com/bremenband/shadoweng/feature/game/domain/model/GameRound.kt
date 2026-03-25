package com.bremenband.shadoweng.feature.game.domain.model

data class GameRound(
    val level: Int,
    val round: Int,
    val sentence: String?,
    val maskedSentence: String?,
    val referenceAudioUrl: String
)