package com.bremenband.shadoweng.feature.mypage.domain.model

data class SessionSummary(
    val sessionId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val completedSentences: Int,
    val totalSentences: Int
) {
    val progressRate: Int get() = if (totalSentences == 0) 0 else (completedSentences * 100 / totalSentences)
    val isCompleted: Boolean get() = totalSentences > 0 && completedSentences >= totalSentences
}