package com.bremenband.shadoweng.feature.mypage.domain.model

data class SessionSummary(
    val sessionId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val completedSentences: Int,
    val totalSentences: Int,
    val isBookmarked: Boolean = false  // TODO: API 연결 후 실제 값으로 교체
) {
    val progressRate: Int get() = if (totalSentences == 0) 0 else (completedSentences * 100 / totalSentences)
    val isCompleted: Boolean get() = totalSentences > 0 && completedSentences >= totalSentences
}