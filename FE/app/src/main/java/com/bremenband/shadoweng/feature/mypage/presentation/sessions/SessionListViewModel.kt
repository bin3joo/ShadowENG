package com.bremenband.shadoweng.feature.mypage.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val repository: MyPageRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    init { loadSessions() }
    fun reload() = loadSessions()

    fun startReview(sessionId: Long) {
        viewModelScope.launch {
            studyRepository.startReview(sessionId)
                .onSuccess { _navigateToStudy.emit(sessionId) }
        }
    }

    fun reLearn(sessionId: Long) {
        viewModelScope.launch {
            studyRepository.reLearn(sessionId)
                .onSuccess { newSessionId -> _navigateToStudy.emit(newSessionId) }
        }
    }

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

    fun toggleSessionBookmark(sessionId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val currentState = _state.value
            val isCurrentlyBookmarked = (currentState.activeSessions + currentState.completedSessions)
                .find { it.sessionId == sessionId }?.isBookmarked ?: false

            repository.toggleSessionBookmark(sessionId, !isCurrentlyBookmarked)
                .onSuccess { newBookmarked ->
                    _state.update { state ->
                        state.copy(
                            activeSessions = state.activeSessions.map {
                                if (it.sessionId == sessionId) it.copy(isBookmarked = newBookmarked) else it
                            },
                            completedSessions = state.completedSessions.map {
                                if (it.sessionId == sessionId) it.copy(isBookmarked = newBookmarked) else it
                            }
                        )
                    }
                    onSuccess()
                }
        }
    }
}