package com.bremenband.shadoweng.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    private val _navigateToGame = MutableSharedFlow<Unit>()
    val navigateToGame: SharedFlow<Unit> = _navigateToGame.asSharedFlow()

    private val _navigateToRegister = MutableSharedFlow<Unit>()
    val navigateToRegister: SharedFlow<Unit> = _navigateToRegister.asSharedFlow()

    private val _navigateToMoreSessions = MutableSharedFlow<Unit>()
    val navigateToMoreSessions: SharedFlow<Unit> = _navigateToMoreSessions.asSharedFlow()

    private val _navigateToStreak = MutableSharedFlow<Unit>()
    val navigateToStreak: SharedFlow<Unit> = _navigateToStreak.asSharedFlow()

    init {
        _uiState.update { it.copy(streak = getDefaultStreak()) }
        loadData()
        loadLeaderboard()
        loadUserMe()
    }


    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.ClickLatestSession ->
                viewModelScope.launch {
                    _uiState.value.latestSession?.let { _navigateToStudy.emit(it.sessionId) }
                }
            is HomeEvent.ClickMoreSessions ->
                viewModelScope.launch { _navigateToMoreSessions.emit(Unit) }
            is HomeEvent.ClickRegister ->
                viewModelScope.launch { _navigateToRegister.emit(Unit) }
            is HomeEvent.ClickGame ->
                viewModelScope.launch { _navigateToGame.emit(Unit) }
            is HomeEvent.ClickStreak ->
                viewModelScope.launch { _navigateToStreak.emit(Unit) }
        }
    }

    private fun getDefaultStreak(): StreakData {
        val javaDay = LocalDate.now().dayOfWeek.value // 월=1 ~ 일=7
        val todayIndex = if (javaDay == 7) 0 else javaDay // 일=0, 월=1...토=6
        val weeklyStatus = List(7) { index ->
            if (todayIndex == 0) index == 0
            else index in 1..todayIndex
        }
        return StreakData(currentDay = javaDay, weeklyStatus = weeklyStatus)
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getRecentSession()
                .onSuccess { session ->
                    _uiState.update { it.copy(isLoading = false, latestSession = session) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            gameRepository.getLeaderboard()
                .onSuccess { leaderboard ->
                    val myRanker = leaderboard.topRankers.find { it.isMe }
                        ?: leaderboard.nearbyRankers.find { it.isMe }
                    val topScore = leaderboard.topRankers.firstOrNull()?.weeklyScore?.toInt() ?: 0
                    val myScore = myRanker?.weeklyScore?.toInt() ?: 0
                    _uiState.update {
                        it.copy(gameSectionData = GameSectionData(topScore, myScore, leaderboard.tier))
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(gameSectionData = GameSectionData(0, 0)) }
                }
        }
    }

    private fun loadUserMe() {
        viewModelScope.launch {
            repository.getUserMe()
                .onSuccess { profile ->
                    val today = LocalDate.now()
                    val dayIndex = today.dayOfWeek.value % 7L  // 월=1~일=7 → 일=0, 월=1...토=6
                    val weekStart = today.minusDays(dayIndex)
                    val weeklyStatus = List(7) { index ->
                        val date = weekStart.plusDays(index.toLong())
                        profile.studyDates.contains(date)
                    }
                    _uiState.update {
                        it.copy(
                            streak = it.streak?.copy(
                                currentDay = profile.totalStudyDays,  // 추가
                                longestStreak = profile.longestStreak,
                                totalVisitedDays = profile.totalVisitedDays,
                                weeklyStatus = weeklyStatus
                            ) ?: StreakData(
                                currentDay = LocalDate.now().dayOfWeek.value,
                                weeklyStatus = weeklyStatus,
                                longestStreak = profile.longestStreak,
                                totalVisitedDays = profile.totalVisitedDays
                            )
                        )
                    }
                }
        }
    }
}