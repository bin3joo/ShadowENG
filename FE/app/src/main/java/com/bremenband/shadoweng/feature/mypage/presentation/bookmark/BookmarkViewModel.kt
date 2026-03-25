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

    private val _state = MutableStateFlow(BookmarkState())
    val state: StateFlow<BookmarkState> = _state.asStateFlow()

    init {
        android.util.Log.d("BookmarkVM", "init called, loading bookmarks")
        loadBookmarks()
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            android.util.Log.d("BookmarkVM", "loadBookmarks called")
            repository.getBookmarks()
                .onSuccess { bookmarks ->
                    android.util.Log.d("BookmarkVM", "success: ${bookmarks.size}")
                    _state.update { it.copy(isLoading = false, bookmarks = bookmarks) }
                }
                .onFailure { e ->
                    android.util.Log.d("BookmarkVM", "failure: ${e.message}")
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    fun toggleBookmark(sentenceId: Long) {
        viewModelScope.launch {
            repository.toggleBookmark(sentenceId, false)
                .onSuccess {
                    _state.update { state ->
                        state.copy(bookmarks = state.bookmarks.filter { it.sentenceId != sentenceId })
                    }
                }
        }
    }
}