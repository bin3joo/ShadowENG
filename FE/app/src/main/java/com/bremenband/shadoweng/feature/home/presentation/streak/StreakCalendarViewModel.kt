package com.bremenband.shadoweng.feature.home.presentation.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.onSuccess

data class StreakCalendarState(
    val longestStreak: Int = 0,
    val totalVisitedDays: Int = 0,
    val totalStudyDays: Int = 0,
    val studyDates: Set<LocalDate> = emptySet(),
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val isLoading: Boolean = true
)

@HiltViewModel
class StreakCalendarViewModel @Inject constructor(
    private val repository: HomeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StreakCalendarState())
    val state: StateFlow<StreakCalendarState> = _state.asStateFlow()

    init { loadStreakInfo() }

    private fun loadStreakInfo() {
        viewModelScope.launch {
            repository.getUserMe()
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            longestStreak = profile.longestStreak,
                            totalVisitedDays = profile.totalVisitedDays,
                            totalStudyDays = profile.totalStudyDays,
                            studyDates = profile.studyDates,
                            isLoading = false
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