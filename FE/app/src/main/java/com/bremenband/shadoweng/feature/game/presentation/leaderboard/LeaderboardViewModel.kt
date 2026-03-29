package com.bremenband.shadoweng.feature.game.presentation.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private val _navigateToPlay = MutableSharedFlow<Unit>()
    val navigateToPlay: SharedFlow<Unit> = _navigateToPlay.asSharedFlow()

    init { load() }
    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getLeaderboard()
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            tier = data.tier,
                            promotionCount = data.promotionCount,
                            daysUntilReset = data.daysUntilReset,
                            hoursUntilReset = data.hoursUntilReset,
                            minutesUntilReset = data.minutesUntilReset,
                            topRankers = data.topRankers,
                            nearbyRankers = data.nearbyRankers,
                            isLoading = false
                        )
                    }
                }.onFailure { e ->
                    // 403 = frozen 상태
                    val frozen = e.message?.contains("403") == true
                    _uiState.update { it.copy(isLoading = false, frozen = frozen, error = if (!frozen) e.message else null) }
                }
        }
    }

    fun onEvent(event: LeaderboardEvent) {
        when (event) {
            is LeaderboardEvent.ClickPlay ->
                viewModelScope.launch { _navigateToPlay.emit(Unit) }
        }
    }
}