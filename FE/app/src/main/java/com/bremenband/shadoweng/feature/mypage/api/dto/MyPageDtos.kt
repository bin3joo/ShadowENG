package com.bremenband.shadoweng.feature.mypage.api.dto

data class UserResponse(val userId: Long, val email: String, val nickname: String, val visitedCount: Int)
data class DailyReportResponse(val studyData: List<DailyStudyData>)
data class DailyStudyData(val date: String, val studiedSentencesCount: Int)

data class ToggleBookmarkRequest(val isBookmarked: Boolean)
data class BookmarkResponse(val sentenceId: Long, val sentence: String, val isBookmarked: Boolean)
data class BookmarkListResponse(
    val bookmarks: List<SessionBookmarkItemDto>
)

data class SessionBookmarkItemDto(
    val sessionId: Long,
    val videoTitle: String?,
    val thumbnailUrl: String?,
    val startSec: Double,
    val endSec: Double,
    val totalSentences: Int,
    val completedSentences: Int
)
data class BookmarkItemResponse(val sentenceId: Long, val sentence: String, val sessionId: Long)

data class ActiveSessionItemDto(
    val sessionId: Long,
    val thumbnails: SessionThumbnailDto?,
    val videoTitle: String? = null,
    val totalSentences: Int,
    val completedSentences: Int
) {
    val progressRate: Int get() = if (totalSentences == 0) 0 else (completedSentences * 100 / totalSentences)
}

data class ActiveSessionsDto(
    val activeSessions: List<ActiveSessionItemDto>,
    val completedSessions: List<ActiveSessionItemDto>
)

data class SessionThumbnailDto(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class SessionBookmarkResponse(
    val sessionId: Long,
    val isBookmarked: Boolean
)