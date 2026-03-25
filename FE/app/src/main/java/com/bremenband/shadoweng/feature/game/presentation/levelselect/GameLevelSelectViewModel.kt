package com.bremenband.shadoweng.feature.game.presentation.levelselect

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
class GameLevelSelectViewModel @Inject constructor(
    private val repository: GameRepository,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameLevelSelectUiState())
    val uiState: StateFlow<GameLevelSelectUiState> = _uiState.asStateFlow()

    private val _navigateToPlay = MutableSharedFlow<Int>() // level
    val navigateToPlay: SharedFlow<Int> = _navigateToPlay.asSharedFlow()

    init {
        reload()
    }

    fun isBgmPlaying(): Boolean = soundManager.isBgmPlaying()

    fun startBgm() { soundManager.playBgmHome() }
    fun stopBgm() { soundManager.stopBgm() }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getToday()
                .onSuccess { levels ->
                    _uiState.update { it.copy(levels = levels, isLoading = false) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onEvent(event: GameLevelSelectEvent) {
        when (event) {
            is GameLevelSelectEvent.SelectLevel -> {
                soundManager.stopBgm()
                soundManager.playGameLevel()
                viewModelScope.launch { _navigateToPlay.emit(event.level) }
            }
            is GameLevelSelectEvent.ToggleRules -> {
                soundManager.playButtonClick()
                _uiState.update { it.copy(isRulesExpanded = !it.isRulesExpanded) }
            }
        }
    }
}