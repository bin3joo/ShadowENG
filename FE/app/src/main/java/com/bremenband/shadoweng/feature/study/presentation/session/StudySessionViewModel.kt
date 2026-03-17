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
            // TODO: 백엔드 연동 후 주석 해제
            // repository.getSession(sessionId)
            //     .onSuccess { session ->
            //         _uiState.update {
            //             it.copy(
            //                 isLoading = false,
            //                 title = session.title,
            //                 embedUrl = session.embedUrl,
            //                 sentences = session.sentences
            //             )
            //         }
            //     }
            //     .onFailure {
            //         _uiState.update { it.copy(isLoading = false) }
            //     }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = "How to Fly with TED",
                    embedUrl = "",
                    sentences = listOf(
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 1L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = true),
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 2L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = true),
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 3L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = false),
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 4L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = false),
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 5L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = false),
                        com.bremenband.shadoweng.feature.study.domain.SentenceItem(id = 6L, content = "I had this meeting with a big studio Hollywood casting director.", timestamp = "0:03", isCompleted = false),
                    )
                )
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