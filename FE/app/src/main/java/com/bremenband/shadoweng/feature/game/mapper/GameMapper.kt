package com.bremenband.shadoweng.feature.game.mapper

import com.bremenband.shadoweng.feature.game.api.dto.*
import com.bremenband.shadoweng.feature.game.domain.model.*

fun GameRoundResponse.toDomain() = GameRound(
    level = level,
    round = round,
    sentence = sentence,
    maskedSentence = maskedSentence,
    referenceAudioUrl = referenceAudioUrl
)

fun GameEvaluateResponse.toDomain() = GameEvalResult(
    userTranscription = userTranscription,
    round = round,
    hearts = hearts,
    totalScore = scores.totalScore,
    gameOver = gameOver,
    finalResult = result?.toDomain(
        missedWords = details.wordFeedback.filter { it.status == "missed" }.map { it.word },
        boundaryToneStatus = details.boundaryToneFeedback.status,
        dynamicStressStatus = details.dynamicStressFeedback.status
    )
)

fun GameFinalResultDto.toDomain(
    missedWords: List<String> = emptyList(),
    boundaryToneStatus: String = "",
    dynamicStressStatus: String = ""
) = GameFinalResult(
    finalScore = finalScore,
    cumulativeScore = cumulativeScore,
    hearts = hearts,
    avgTotalScore = avgTotalScore,
    avgSpeedSimilarity = avgSpeedSimilarity,
    avgDynamicStressScore = avgDynamicStressScore,
    avgBoundaryToneScore = avgBoundaryToneScore,
    missedWords = missedWords,
    boundaryToneStatus = boundaryToneStatus,
    dynamicStressStatus = dynamicStressStatus
)

fun LevelStatusDto.toDomain() = LevelStatus(
    level = level,
    unlocked = unlocked,
    todayBestHearts = todayBest?.hearts,
    todayBestScore = todayBest?.finalScore
)

fun GameLeaderboardResponse.toDomain() = GameLeaderboard(
    tier = tier,
    promotionCount = promotionCount,
    daysUntilReset = timeUntilReset.days,
    hoursUntilReset = timeUntilReset.hours,
    minutesUntilReset = timeUntilReset.minutes,
    topRankers = topRankers.map { it.toDomain() },
    nearbyRankers = nearbyRankers.map { it.toDomain() }
)

fun RankerDto.toDomain() = Ranker(
    rank = rank,
    nickname = nickname,
    weeklyScore = weeklyScore,
    isMe = isMe
)

fun GameProfileResponse.toDomain() = GameProfile(
    tier = tier,
    weeklyScore = weeklyScore,
    frozen = frozen
)