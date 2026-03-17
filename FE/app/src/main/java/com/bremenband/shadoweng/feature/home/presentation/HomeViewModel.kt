package com.bremenband.shadoweng.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val repository: HomeRepository
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

    private fun getDefaultStreak(): StreakData {
        val dayOfWeek = LocalDate.now().dayOfWeek.value // 1=월 ~ 7=일
        val weeklyStatus = List(7) { index -> index < dayOfWeek }
        return StreakData(currentDay = dayOfWeek, weeklyStatus = weeklyStatus)
    }

    init {
        _uiState.update { it.copy(streak = getDefaultStreak()) }
        loadData()
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
        }
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
}