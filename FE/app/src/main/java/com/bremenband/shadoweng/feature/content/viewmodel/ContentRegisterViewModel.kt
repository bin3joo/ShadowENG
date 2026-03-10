package com.bremenband.shadoweng.feature.content.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.content.model.ContentRegisterEvent
import com.bremenband.shadoweng.feature.content.model.ContentRegisterUiState
import com.bremenband.shadoweng.feature.content.repository.ContentRepository
import com.bremenband.shadoweng.feature.content.util.YoutubeUrlParser
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
class ContentRegisterViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentRegisterUiState())
    val uiState: StateFlow<ContentRegisterUiState> = _uiState.asStateFlow()

    private val _navigateToRange = MutableSharedFlow<String>()
    val navigateToRange: SharedFlow<String> = _navigateToRange.asSharedFlow()

    fun onEvent(event: ContentRegisterEvent) {
        when (event) {
            is ContentRegisterEvent.UrlChanged -> {
                val videoId = YoutubeUrlParser.extractVideoId(event.url)
                _uiState.update {
                    it.copy(url = event.url, videoId = videoId, isValidUrl = videoId != null, error = null)
                }
            }
            is ContentRegisterEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val videoId = _uiState.value.videoId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getVideo("https://www.youtube.com/watch?v=$videoId")
                .onSuccess { video -> _navigateToRange.emit(video.embedUrl) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}