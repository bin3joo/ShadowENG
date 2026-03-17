package com.bremenband.shadoweng.feature.mypage.api.dto

data class UserResponse(val userId: Long, val email: String, val nickname: String, val visitedCount: Int)
data class DailyReportResponse(val studyData: List<DailyStudyData>)
data class DailyStudyData(val date: String, val studiedSentencesCount: Int)

data class ToggleBookmarkRequest(val isBookmarked: Boolean)
data class BookmarkResponse(val sentenceId: Long, val sentence: String, val isBookmarked: Boolean)
data class BookmarkListResponse(val bookmarks: List<BookmarkItemResponse>)
data class BookmarkItemResponse(val sentenceId: Long, val sentence: String, val sessionId: Long)