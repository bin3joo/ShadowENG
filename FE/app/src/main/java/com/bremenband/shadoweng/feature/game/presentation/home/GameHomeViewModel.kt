package com.bremenband.shadoweng.feature.game.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.audio.SoundManager
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
class GameHomeViewModel @Inject constructor(
    private val repository: GameRepository,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameHomeUiState())
    val uiState: StateFlow<GameHomeUiState> = _uiState.asStateFlow()

    private val _navigateToPlay = MutableSharedFlow<Unit>()
    val navigateToPlay: SharedFlow<Unit> = _navigateToPlay.asSharedFlow()

    private val _navigateToLeaderboard = MutableSharedFlow<Unit>()
    val navigateToLeaderboard: SharedFlow<Unit> = _navigateToLeaderboard.asSharedFlow()

    init { load() }
    fun reload() = load()

    fun startBgm() {
        soundManager.playBgmHome()
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.stopBgm()
    }

    fun stopBgm() {
        soundManager.stopBgm()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getToday().onSuccess { levels ->
                _uiState.update { it.copy(levels = levels, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
            repository.getProfile().onSuccess { profile ->
                _uiState.update {
                    it.copy(profile = ProfileInfo(profile.tier, profile.weeklyScore, profile.frozen))
                }
            }
        }
    }

    fun onEvent(event: GameHomeEvent) {
        when (event) {
            is GameHomeEvent.ClickPlay ->
                viewModelScope.launch { _navigateToPlay.emit(Unit) }
            is GameHomeEvent.ClickLeaderboard ->
                viewModelScope.launch { _navigateToLeaderboard.emit(Unit) }
        }
    }
}