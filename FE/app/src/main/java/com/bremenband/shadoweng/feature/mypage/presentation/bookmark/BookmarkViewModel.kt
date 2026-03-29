package com.bremenband.shadoweng.feature.mypage.presentation.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val repository: MyPageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BookmarkUiState())
    val state: StateFlow<BookmarkUiState> = _state.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    init { loadBookmarks() }
    fun reload() = loadBookmarks()

    private fun loadBookmarks() {
        viewModelScope.launch {
            repository.getBookmarkedSessions()
                .onSuccess { sessions ->
                    _state.update { it.copy(isLoading = false, bookmarkedSessions = sessions) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    fun toggleBookmark(sessionId: Long) {
        viewModelScope.launch {
            repository.toggleSessionBookmark(sessionId, false)
                .onSuccess {
                    _state.update { state ->
                        state.copy(bookmarkedSessions = state.bookmarkedSessions.filter { it.sessionId != sessionId })
                    }
                }
        }
    }

    fun navigateToSession(sessionId: Long) {
        viewModelScope.launch { _navigateToStudy.emit(sessionId) }
    }
}