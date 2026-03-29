package com.bremenband.shadoweng.feature.mypage.presentation.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import com.bremenband.shadoweng.feature.home.repository.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MyPageUiState())
    val state: StateFlow<MyPageUiState> = _state.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    private val _navigateToRegister = MutableSharedFlow<Unit>()
    val navigateToRegister: SharedFlow<Unit> = _navigateToRegister.asSharedFlow()

    private val _navigateToGame = MutableSharedFlow<Unit>()
    val navigateToGame: SharedFlow<Unit> = _navigateToGame.asSharedFlow()

    init { loadAll() }
    fun reload() = loadAll()

    private fun loadAll() {
        viewModelScope.launch {
            homeRepository.getUserMe()
                .onSuccess { profile ->
                    _state.update { it.copy(nickname = profile.nickname, email = profile.email ?: "",) }
                }

            homeRepository.getRecentSession()
                .onSuccess { session ->
                    _state.update { it.copy(latestSession = session, isLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }

            gameRepository.getLeaderboard()
                .onSuccess { leaderboard ->
                    val myRanker = leaderboard.topRankers.find { it.isMe }
                        ?: leaderboard.nearbyRankers.find { it.isMe }
                    _state.update {
                        it.copy(
                            leagueTopScore = leaderboard.topRankers.firstOrNull()?.weeklyScore?.roundToInt() ?: 0,
                            myScore = myRanker?.weeklyScore?.roundToInt() ?: 0,
                            tier = leaderboard.tier
                        )
                    }
                }
        }
    }

    fun onEvent(event: MyPageEvent) {
        when (event) {
            is MyPageEvent.ClickRegister ->
                viewModelScope.launch { _navigateToRegister.emit(Unit) }
            is MyPageEvent.ClickLatestSession ->
                viewModelScope.launch {
                    _state.value.latestSession?.let { _navigateToStudy.emit(it.sessionId) }
                }
            is MyPageEvent.ClickGame ->
                viewModelScope.launch { _navigateToGame.emit(Unit) }
        }
    }
}