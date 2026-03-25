package com.bremenband.shadoweng.feature.study.presentation.learning

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.audio.AudioRecorder
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StudyLearningViewModel @Inject constructor(
    private val repository: StudyRepository,
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyLearningUiState())
    val uiState: StateFlow<StudyLearningUiState> = _uiState.asStateFlow()

    private val _navigateToHighlight = MutableSharedFlow<Int>()
    val navigateToHighlight: SharedFlow<Int> = _navigateToHighlight.asSharedFlow()

    private val _navigateToReport = MutableSharedFlow<Unit>()
    val navigateToReport: SharedFlow<Unit> = _navigateToReport.asSharedFlow()

    private var countdownJob: Job? = null
    private var autoAdvanceJob: Job? = null
    private var currentSessionId: Long = 0L
    private var currentStep: Int = 1
    private var recordingFile: File? = null

    fun init(sessionId: Long, sentence: SentenceItem, step: Int) {
        currentSessionId = sessionId
        currentStep = step
        viewModelScope.launch {
            repository.getSession(sessionId)
                .onSuccess { session ->
                    Log.d("StudyLearning", "embedUrl: ${session.embedUrl}")
                    Log.d("StudyLearning", "watchUrl: ${session.watchUrl}")
                    val targetSentence = session.sentences.find { it.id == sentence.id } ?: sentence
                    _uiState.update {
                        it.copy(
                            sentence = targetSentence,
                            embedUrl = session.embedUrl.ifEmpty { session.watchUrl },
                            thumbnailUrl = session.thumbnailUrl ?: "",
                            startSec = targetSentence.startSec,
                            endSec = targetSentence.endSec,
                            subtitleMode = stepToSubtitleMode(step)
                        )
                    }
                }
        }
    }

    private fun stepToSubtitleMode(step: Int) = when (step) {
        1 -> SubtitleMode.NONE
        2 -> SubtitleMode.FULL
        3 -> SubtitleMode.PARTIAL
        4 -> SubtitleMode.NONE_FINAL
        else -> SubtitleMode.NONE
    }

    fun onEvent(event: StudyLearningEvent) {
        when (event) {
            is StudyLearningEvent.StartRecording -> startRecording()   // 변경
            is StudyLearningEvent.StopRecording -> stopRecording()
            is StudyLearningEvent.RetryRecording -> {
                countdownJob?.cancel()
                audioRecorder.release()
                recordingFile = null
                _uiState.update { it.copy(isRecording = false) }   // countdown 제거
            }
        }
    }

    private fun startRecording() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true) }
            try {
                recordingFile = audioRecorder.start()
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false, error = "녹음을 시작할 수 없어요. 마이크 권한을 확인해주세요.") }
            }
        }
    }

    private fun stopRecording() {
        val sentenceId = _uiState.value.sentence?.id ?: return
        val file = audioRecorder.stop()
        _uiState.update { it.copy(isRecording = false) }

        if (currentStep == 1) {
            _uiState.update { it.copy(showEncourageModal = true) }
            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(showEncourageModal = false) }
                handlePostRecording()
            }
            return
        }

        recordingFile = file
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            if (file != null) {
                repository.createEvaluation(
                    sessionId = currentSessionId,
                    sentenceId = sentenceId,
                    step = currentStep,
                    audioFile = file
                ).onSuccess {
                    _uiState.update { it.copy(isAnalyzing = false) }
                    handlePostRecording()
                }.onFailure { e ->
                    _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
                    handlePostRecording()
                }
            } else {
                _uiState.update { it.copy(isAnalyzing = false) }
                handlePostRecording()
            }
        }
    }

    private suspend fun handlePostRecording() {
        _uiState.update { it.copy(isNavigating = true) }  // 로딩 시작
        if (currentStep == 1) {
            _navigateToHighlight.emit(currentStep)
        } else {
            delay(500)  // 약간의 딜레이 후 이동
            _navigateToHighlight.emit(currentStep)
        }
    }

    fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }
}