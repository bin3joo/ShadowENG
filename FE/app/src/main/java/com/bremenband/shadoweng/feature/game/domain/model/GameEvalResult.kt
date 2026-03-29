package com.bremenband.shadoweng.feature.game.domain.model

data class GameEvalResult(
    val userTranscription: String,
    val round: Int,
    val hearts: Int,
    val totalScore: Double,
    val gameOver: Boolean,
    val finalResult: GameFinalResult?
)

data class GameFinalResult(
    val finalScore: Double,
    val hearts: Int,
    val avgTotalScore: Double,
    val avgWordRhythmScore: Double,
    val avgDynamicStressScore: Double,
    val avgWordAccuracy: Double,
    val missedWords: List<String> = emptyList(),
    val boundaryToneStatus: String = "",
    val dynamicStressStatus: String = ""
)