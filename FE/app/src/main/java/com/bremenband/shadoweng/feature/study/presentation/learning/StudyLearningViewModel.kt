package com.bremenband.shadoweng.feature.study.presentation.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyLearningViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyLearningUiState())
    val uiState: StateFlow<StudyLearningUiState> = _uiState.asStateFlow()
    val navigateToHighlight = MutableSharedFlow<Long>()
    val navigateToReport = MutableSharedFlow<Unit>()
    val showAutoAdvanceSnackbar = MutableSharedFlow<Unit>()

    private var countdownJob: Job? = null
    private var autoAdvanceJob: Job? = null
    private var currentSessionId: Long = 0L

    fun init(sessionId: Long, sentence: SentenceItem) {
        currentSessionId = sessionId
        // TODO: 백엔드 연동 후 실제 sentence로 교체
        _uiState.update {
            it.copy(
                sentence = sentence.copy(
                    content = "I had this meeting with a big studio Hollywood casting director."
                )
            )
        }
    }

    fun onEvent(event: StudyLearningEvent) {
        when (event) {
            is StudyLearningEvent.StartCountdown -> startCountdown()
            is StudyLearningEvent.StopRecording -> stopRecording()
            is StudyLearningEvent.RetryRecording -> {
                countdownJob?.cancel()
                _uiState.update { it.copy(countdown = null, isRecording = false) }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(countdown = null, isRecording = true) }
        }
    }

    private fun stopRecording() {
        val sentenceId = _uiState.value.sentence?.id ?: return
        _uiState.update { it.copy(isRecording = false, isAnalyzing = true) }
        viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isAnalyzing = false) }
            val mode = _uiState.value.subtitleMode
            if (mode == SubtitleMode.NONE_FINAL) {
                navigateToReport.emit(Unit) // highlight 건너뛰고 report로
            } else {
                showAutoAdvanceSnackbar.emit(Unit)
                autoAdvanceJob?.cancel()
                autoAdvanceJob = launch {
                    delay(3000)
                    applyNextMode()
                }
            }
        }
    }

    fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    fun nextMode(): SubtitleMode? = when (_uiState.value.subtitleMode) {
        SubtitleMode.NONE -> SubtitleMode.FULL
        SubtitleMode.FULL -> SubtitleMode.PARTIAL
        SubtitleMode.PARTIAL -> SubtitleMode.NONE_FINAL
        SubtitleMode.NONE_FINAL -> null
    }

    fun applyNextMode() {
        val next = nextMode() ?: return
        _uiState.update { it.copy(subtitleMode = next, countdown = null, isRecording = false, isAnalyzing = false) }
    }
}