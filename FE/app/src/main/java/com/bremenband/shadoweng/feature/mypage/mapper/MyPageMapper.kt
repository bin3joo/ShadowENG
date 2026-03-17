package com.bremenband.shadoweng.feature.mypage.mapper

import com.bremenband.shadoweng.feature.study.api.dto.ActiveSession
import com.bremenband.shadoweng.feature.study.api.dto.SessionResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.BookmarkItemResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.DailyReportResponse
import com.bremenband.shadoweng.feature.mypage.presentation.BookmarkItem
import com.bremenband.shadoweng.feature.mypage.presentation.LearningContent

fun SessionResponse.toLearningContent() = LearningContent(
    id = sessionId,
    title = videoData.title,
    thumbnailUrl = videoData.thumbnailUrl,
    totalSentences = sentencesData.size,
    completedSentences = sentencesData.count { it.studyCount > 0 }
)

fun BookmarkItemResponse.toDomain() = BookmarkItem(
    id = sentenceId,
    sentence = sentence,
    sessionId = sessionId
)

fun DailyReportResponse.toDailyCount(): Int =
    studyData.sumOf { it.studiedSentencesCount }

fun ActiveSession.toLearningContent() = LearningContent(
    id = sessionId,
    title = "",
    thumbnailUrl = thumbnailUrl,
    totalSentences = 100,
    completedSentences = progressRate
)