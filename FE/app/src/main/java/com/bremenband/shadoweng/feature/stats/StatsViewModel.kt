package com.bremenband.shadoweng.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { loadStats() }

    private fun loadStats() {
        viewModelScope.launch {
            homeRepository.getDashboard()
                .onSuccess { dashboard ->
                    val reportDates = dashboard.reportDates.associate { reportDate ->
                        LocalDate.parse(reportDate.date) to reportDate.reports.map { r ->
                            ReportSummaryUi(
                                sessionId = r.sessionId,
                                reportId = r.reportId,
                                title = r.videoTitle ?: "",
                                thumbnailUrl = r.thumbnailUrl ?: "",
                                studyScore = r.totalScore?.roundToInt() ?: 0,
                                reviewScore = 0,
                                date = reportDate.date
                            )
                        }
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            longestStreak = dashboard.longestStreak,
                            totalVisitedDays = dashboard.totalAttendanceDays,
                            totalSentences = dashboard.totalStudiedSentences,
                            totalMinutes = (dashboard.totalStudyTimeSeconds / 60).toInt(),
                            studyDates = dashboard.studyDates.map { LocalDate.parse(it) }.toSet(),
                            reportDates = reportDates
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false) }
                    e.printStackTrace()
                }
        }
    }

    fun prevMonth() { _state.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) } }
    fun nextMonth() { _state.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) } }
}