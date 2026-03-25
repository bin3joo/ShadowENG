package com.bremenband.shadoweng.feature.game.api.dto

import okhttp3.MultipartBody

// ── Today ──────────────────────────────────────────────
data class GameTodayResponse(
    val levels: List<LevelStatusDto>
)
data class LevelStatusDto(
    val level: Int,
    val unlocked: Boolean,
    val todayBest: TodayBestDto?
)
data class TodayBestDto(
    val hearts: Int,
    val finalScore: Double
)

// ── Round Sentence ─────────────────────────────────────
data class GameRoundResponse(
    val level: Int,
    val round: Int,
    val sentence: String?,
    val maskedSentence: String?,
    val referenceAudioUrl: String
)

// ── Evaluate ───────────────────────────────────────────
data class GameEvaluateResponse(
    val userTranscription: String,
    val details: GameEvaluateDetails,
    val scores: GameEvaluateScores,
    val round: Int,
    val hearts: Int,
    val gameOver: Boolean,
    val result: GameFinalResultDto?
)
data class GameEvaluateDetails(
    val wordFeedback: List<GameWordFeedback>,
    val boundaryToneFeedback: GameToneFeedback,
    val dynamicStressFeedback: GameToneFeedback
)
data class GameWordFeedback(val word: String, val status: String)
data class GameToneFeedback(val status: String)
data class GameEvaluateScores(
    val totalScore: Double,
    val wordAccuracy: Double,
    val prosodyAndStress: Double,
    val wordRhythmScore: Double,
    val boundaryToneScore: Double,
    val dynamicStressScore: Double,
    val speedSimilarity: Double,
    val pauseSimilarity: Double
)
data class GameFinalResultDto(
    val avgTotalScore: Double,
    val avgSpeedSimilarity: Double,
    val avgDynamicStressScore: Double,
    val avgBoundaryToneScore: Double,
    val finalScore: Double,
    val cumulativeScore: Double,
    val hearts: Int
)

// ── Leaderboard ────────────────────────────────────────
data class GameLeaderboardResponse(
    val tier: String,
    val promotionCount: Int,
    val timeUntilReset: TimeUntilReset,
    val topRankers: List<RankerDto>,
    val nearbyRankers: List<RankerDto>
)
data class TimeUntilReset(val days: Int, val hours: Int, val minutes: Int)
data class RankerDto(
    val rank: Int,
    val nickname: String,
    val weeklyScore: Double,
    val isMe: Boolean
)

// ── Profile ────────────────────────────────────────────
data class GameProfileResponse(
    val tier: String,
    val weeklyScore: Double,
    val frozen: Boolean
)