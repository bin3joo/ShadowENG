package com.bremenband.shadoweng.feature.mypage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.mypage.repository.MyPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
class MyPageViewModel @Inject constructor(
    private val repository: MyPageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState())
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    init { loadData() }

    private val _navigateToContent = MutableSharedFlow<Long>()
    val navigateToContent: SharedFlow<Long> = _navigateToContent.asSharedFlow()

    fun onEvent(event: MyPageEvent) {
        when (event) {
            is MyPageEvent.ClickContent ->
                viewModelScope.launch { _navigateToContent.emit(event.contentId) }
            is MyPageEvent.Refresh -> loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val sessionsDeferred = async { repository.getSessions() }
            val bookmarksDeferred = async { repository.getBookmarks() }
            val dailyDeferred = async { repository.getDailyCount() }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    learningContents = sessionsDeferred.await().getOrElse { emptyList() },
                    bookmarks = bookmarksDeferred.await().getOrElse { emptyList() },
                    dailySentenceCount = dailyDeferred.await().getOrElse { 0 }
                )
            }
        }
    }
}