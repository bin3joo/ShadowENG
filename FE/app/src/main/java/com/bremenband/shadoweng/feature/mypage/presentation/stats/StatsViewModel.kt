package com.bremenband.shadoweng.feature.mypage.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init { loadStats() }

    private fun loadStats() {
        viewModelScope.launch {
            // TODO: API 연결
            _state.update {
                it.copy(
                    isLoading = false,
                    totalSentences = 158,
                    totalMinutes = 12345,
                    reports = listOf(
                        ReportSummaryUi(
                            sessionId = 1L,
                            reportId = 1L,
                            title = "How to Fly with TED",
                            thumbnailUrl = "",
                            studyScore = 60,
                            reviewScore = 70,
                            date = "2026.03.19"
                        ),
                        ReportSummaryUi(
                            sessionId = 2L,
                            reportId = 2L,
                            title = "A 3-Step Guide to Believing",
                            thumbnailUrl = "",
                            studyScore = 72,
                            reviewScore = 80,
                            date = "2026.03.18"
                        ),
                    )
                )
            }
        }
    }
}