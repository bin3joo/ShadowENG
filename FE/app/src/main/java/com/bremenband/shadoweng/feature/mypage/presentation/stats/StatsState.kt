package com.bremenband.shadoweng.feature.mypage.presentation.stats

data class StatsState(
    val totalSentences: Int = 0,
    val totalMinutes: Int = 0,
    val reports: List<ReportSummaryUi> = emptyList(),
    val isLoading: Boolean = true
)

data class ReportSummaryUi(
    val sessionId: Long,
    val reportId: Long,
    val title: String,
    val thumbnailUrl: String,
    val studyScore: Int,
    val reviewScore: Int,
    val date: String
)