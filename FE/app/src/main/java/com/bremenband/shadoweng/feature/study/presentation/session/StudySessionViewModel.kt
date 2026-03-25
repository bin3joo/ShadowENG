package com.bremenband.shadoweng.feature.study.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudySessionViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudySessionUiState())
    val uiState: StateFlow<StudySessionUiState> = _uiState.asStateFlow()
    val navigateToLearning = MutableSharedFlow<Long>()

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSession(sessionId)
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = session.title,
                            embedUrl = session.embedUrl,
                            thumbnailUrl = session.thumbnailUrl ?: "",
                            sentences = session.sentences
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun onEvent(event: StudySessionEvent) {
        when (event) {
            is StudySessionEvent.SelectSentence ->
                viewModelScope.launch { navigateToLearning.emit(event.sentenceId) }
            is StudySessionEvent.StartStudy ->
                viewModelScope.launch {
                    _uiState.value.selectedSentenceId?.let { navigateToLearning.emit(it) }
                }
        }
    }
}