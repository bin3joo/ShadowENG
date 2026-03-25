package com.bremenband.shadoweng.feature.mypage.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val repository: MyPageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListState())
    val state: StateFlow<SessionListState> = _state.asStateFlow()

    init { loadSessions() }

    fun loadSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.getSessions()
                .onSuccess { sessions ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            activeSessions = sessions.filter { s -> !s.isCompleted },
                            completedSessions = sessions.filter { s -> s.isCompleted }
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toString()) }
                }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
                .onSuccess { loadSessions() }
        }
    }
}