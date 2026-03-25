package com.bremenband.shadoweng.feature.mypage.mapper

import com.bremenband.shadoweng.feature.mypage.api.dto.ActiveSessionItemDto
import com.bremenband.shadoweng.feature.mypage.api.dto.BookmarkItemResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.DailyReportResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.UserResponse
import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence
import com.bremenband.shadoweng.feature.mypage.domain.model.MyPageProfile
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary
import com.bremenband.shadoweng.feature.study.api.dto.ActiveSession

fun UserResponse.toDomain() = MyPageProfile(
    userId = userId,
    nickname = nickname,
    email = email,
    visitedCount = visitedCount
)

fun ActiveSession.toDomain() = SessionSummary(
    sessionId = sessionId,
    title = "",           // TODO: API 필드 추가 후 연결
    thumbnailUrl = thumbnailUrl,
    completedSentences = progressRate,
    totalSentences = 100  // TODO: API 필드 추가 후 연결
)

fun BookmarkItemResponse.toDomain() = BookmarkedSentence(
    sentenceId = sentenceId,
    sentence = sentence,
    sessionId = sessionId
)

fun DailyReportResponse.toDailyCount(): Int =
    studyData.sumOf { it.studiedSentencesCount }

fun ActiveSessionItemDto.toDomain(isCompleted: Boolean = false): SessionSummary = SessionSummary(
    sessionId = sessionId,
    title = videoTitle ?: "",
    thumbnailUrl = thumbnails?.url,
    completedSentences = completedSentences,
    totalSentences = totalSentences
)