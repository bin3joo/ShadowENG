package com.bremenband.shadoweng.feature.home.mapper

import com.bremenband.shadoweng.feature.home.presentation.LatestSession
import com.bremenband.shadoweng.feature.home.api.dto.UserMeDto
import com.bremenband.shadoweng.feature.home.domain.model.UserProfile
import com.bremenband.shadoweng.feature.study.api.dto.LatestActiveSessionDto
import java.time.LocalDate

fun LatestActiveSessionDto.toLatestSession(): LatestSession = LatestSession(
    sessionId = sessionId,
    thumbnailUrl = thumbnailUrl ?: "",
    progressRate = if ((totalSentences ?: 0) == 0) 0
    else ((completedSentences ?: 0) * 100 / totalSentences!!),
    title = videoTitle ?: "",
    completedSentences = completedSentences ?: 0,
    totalSentences = totalSentences ?: 0
)

fun UserMeDto.toUserProfile(): UserProfile = UserProfile(
    userId = userId,
    nickname = nickname,
    email = email ?: "",
    totalVisitedDays = totalVisitedDays,
    totalStudyDays = totalStudyDays,
    longestStreak = longestStreak,
    studyDates = studyDates.map { LocalDate.parse(it) }.toSet(),
    createdAt = createdAt
)