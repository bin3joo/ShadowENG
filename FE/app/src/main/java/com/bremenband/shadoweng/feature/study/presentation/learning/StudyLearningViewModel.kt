package com.bremenband.shadoweng.feature.study.presentation.learning

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.audio.AudioRecorder
import com.bremenband.shadoweng.core.exception.DomainException
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
    private var recordingStartTime: Long = 0L

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
            is StudyLearningEvent.StartRecording -> startRecording()
            is StudyLearningEvent.StopRecording -> stopRecording()
            is StudyLearningEvent.RetryRecording -> {
                countdownJob?.cancel()
                audioRecorder.release()
                recordingFile = null
                _uiState.update { it.copy(isRecording = false) }
            }
            is StudyLearningEvent.DismissVoiceModal ->
                _uiState.update { it.copy(showVoiceNotRecognizedModal = false) }
            is StudyLearningEvent.DismissTooShortModal ->
                _uiState.update { it.copy(showTooShortModal = false) }
        }
    }

    private fun startRecording() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true) }
            try {
                recordingFile = audioRecorder.start()
                recordingStartTime = System.currentTimeMillis()  // 추가
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRecording = false, error = "녹음을 시작할 수 없어요. 마이크 권한을 확인해주세요.")
                }
            }
        }
    }

    private fun stopRecording() {
        val sentenceId = _uiState.value.sentence?.id ?: return
        val file = audioRecorder.stop()
        val recordingDuration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
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

        // 너무 짧은 발화 체크 (sentence duration의 0.5배 미만)
        val sentenceDuration = _uiState.value.endSec - _uiState.value.startSec
        if (sentenceDuration > 0 && recordingDuration < sentenceDuration * 0.5) {
            recordingFile = null
            _uiState.update { it.copy(showTooShortModal = true) }
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
                    _uiState.update { it.copy(isAnalyzing = false) }
                    when {
                        e.message?.contains("음성이 인식되지 않았습니다") == true -> {
                            _uiState.update { it.copy(showVoiceNotRecognizedModal = true) }
                        }
                        else -> handlePostRecording()
                    }
                }
            } else {
                _uiState.update { it.copy(isAnalyzing = false) }
                handlePostRecording()
            }
        }
    }

    private suspend fun handlePostRecording() {
        _uiState.update { it.copy(isNavigating = true) }
        if (currentStep == 1) {
            _navigateToHighlight.emit(currentStep)
        } else {
            delay(500)
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