package com.bremenband.shadoweng.feature.stats

import java.time.LocalDate

data class StatsUiState(
    val totalSentences: Int = 0,
    val totalMinutes: Int = 0,
    val reports: List<ReportSummaryUi> = emptyList(),
    val longestStreak: Int = 0,
    val totalVisitedDays: Int = 0,
    val studyDates: Set<LocalDate> = emptySet(),
    val reportDates: Map<LocalDate, List<ReportSummaryUi>> = emptyMap(),  // 추가
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
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