package com.bremenband.shadoweng.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.auth.repository.AuthRepository
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import com.bremenband.shadoweng.feature.profile.api.MyInfoApi
import com.bremenband.shadoweng.feature.profile.api.UpdateNicknameRequest
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
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
import kotlin.math.roundToInt

@HiltViewModel
class MyInfoViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val gameRepository: GameRepository,
    private val myPageRepository: MyPageRepository,
    private val authRepository: AuthRepository,
    private val myInfoApi: MyInfoApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyInfoUiState())
    val uiState: StateFlow<MyInfoUiState> = _uiState.asStateFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    init { loadAll() }
    fun reload() = loadAll()

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                homeRepository.getUserMe()
                    .onSuccess { profile ->
                        _uiState.update {
                            it.copy(
                                nickname = profile.nickname,
                                email = profile.email ?: "",
                                totalVisitedDays = profile.totalVisitedDays,
                                longestStreak = profile.longestStreak
                            )
                        }
                    }

                gameRepository.getProfile()
                    .onSuccess { profile ->
                        _uiState.update {
                            it.copy(tier = profile.tier, weeklyScore = profile.weeklyScore.roundToInt())
                        }
                    }

                myPageRepository.getBookmarkedSessions()
                    .onSuccess { sessions ->
                        _uiState.update { it.copy(bookmarkCount = sessions.size) }
                    }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: MyInfoEvent) {
        when (event) {
            is MyInfoEvent.ClickLogout -> {
                viewModelScope.launch {
                    authRepository.logout()
                        .onSuccess { _navigateToLogin.emit(Unit) }
                        .onFailure { _navigateToLogin.emit(Unit) }
                }
            }
            is MyInfoEvent.ClickEditNickname -> {
                _uiState.update { it.copy(showNicknameDialog = true, nicknameInput = it.nickname) }
            }
            is MyInfoEvent.DismissNicknameDialog -> {
                _uiState.update { it.copy(showNicknameDialog = false) }
            }
            is MyInfoEvent.NicknameInputChanged -> {
                _uiState.update { it.copy(nicknameInput = event.value) }
            }
            is MyInfoEvent.ConfirmNicknameChange -> {
                viewModelScope.launch {
                    val newNickname = _uiState.value.nicknameInput.trim()
                    if (newNickname.isBlank()) return@launch
                    _uiState.update { it.copy(isNicknameUpdating = true) }
                    runCatching { myInfoApi.updateNickname(UpdateNicknameRequest(newNickname)) }
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    nickname = newNickname,
                                    showNicknameDialog = false,
                                    isNicknameUpdating = false
                                )
                            }
                        }
                        .onFailure {
                            _uiState.update { it.copy(isNicknameUpdating = false) }
                        }
                }
            }
        }
    }
}