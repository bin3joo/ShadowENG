package com.bremenband.shadoweng.feature.mypage.presentation

data class LearningContent(
    val id: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val totalSentences: Int,
    val completedSentences: Int,
    val progress: Float = if (totalSentences > 0) completedSentences / totalSentences.toFloat() else 0f
)

data class BookmarkItem(
    val id: Long,
    val sentence: String,
    val sessionId: Long,
    val contentTitle: String = "" // TODO: 백엔드 응답에 포함 요청
)

data class MyPageUiState(
    val dailySentenceCount: Int = 0,
    val learningContents: List<LearningContent> = emptyList(),
    val bookmarks: List<BookmarkItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class MyPageEvent {
    data class ClickContent(val contentId: Long) : MyPageEvent()
    object Refresh : MyPageEvent()
}