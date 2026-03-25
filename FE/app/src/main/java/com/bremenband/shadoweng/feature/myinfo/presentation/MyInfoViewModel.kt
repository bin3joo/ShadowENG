package com.bremenband.shadoweng.feature.myinfo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.auth.repository.AuthRepository
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
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

data class MyInfoUiState(
    val nickname: String = "",
    val email: String = "",
    val tier: String = "",
    val totalVisitedDays: Int = 0,
    val longestStreak: Int = 0,
    val bookmarkCount: Int = 0,
    val weeklyScore: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class MyInfoEvent {
    object ClickLogout : MyInfoEvent()
}

@HiltViewModel
class MyInfoViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val gameRepository: GameRepository,
    private val myPageRepository: MyPageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyInfoUiState())
    val uiState: StateFlow<MyInfoUiState> = _uiState.asStateFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            homeRepository.getUserMe()
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            nickname = profile.nickname,
                            email = profile.email,
                            totalVisitedDays = profile.totalVisitedDays,
                            longestStreak = profile.longestStreak
                        )
                    }
                }

            gameRepository.getProfile()
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            tier = profile.tier,
                            weeklyScore = profile.weeklyScore.toInt()
                        )
                    }
                }

            myPageRepository.getBookmarks()
                .onSuccess { bookmarks ->
                    _uiState.update { it.copy(bookmarkCount = bookmarks.size) }
                }

            _uiState.update { it.copy(isLoading = false) }
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
        }
    }
}