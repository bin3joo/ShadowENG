package com.bremenband.shadoweng.feature.game.presentation.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.audio.AudioRecorder
import com.bremenband.shadoweng.core.audio.SoundManager
import com.bremenband.shadoweng.feature.game.domain.model.GameFinalResult
import com.bremenband.shadoweng.feature.game.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GamePlayViewModel @Inject constructor(
    private val repository: GameRepository,
    private val audioRecorder: AudioRecorder,
    private val soundManager: SoundManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val level: Int = checkNotNull(savedStateHandle["level"])
    private val prevBest: Double = savedStateHandle.get<Float>("prevBest")?.toDouble() ?: 0.0  // 여기로 이동

    private val _state = MutableStateFlow(GamePlayState(level = level))
    val state: StateFlow<GamePlayState> = _state.asStateFlow()

    private val _effect = Channel<GamePlayEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var countdownJob: Job? = null
    private var recordingFile: File? = null

    init { loadRound(level, 1) }

    fun onIntent(intent: GamePlayIntent) {
        when (intent) {
            is GamePlayIntent.StartCountdown -> startCountdown()
            is GamePlayIntent.StopRecording -> stopRecording()
            is GamePlayIntent.RetryCountdown -> {
                countdownJob?.cancel()
                audioRecorder.release()
                recordingFile = null
                _state.update { it.copy(countdown = null, isRecording = false) }
            }
            is GamePlayIntent.SetAudioPlaying ->
                _state.update { it.copy(isPlayingAudio = intent.isPlaying) }
            is GamePlayIntent.DismissVoiceModal -> {
                _state.update { it.copy(showVoiceNotRecognizedModal = false) }
            }
        }
    }
    private fun loadRound(level: Int, round: Int) {
        viewModelScope.launch {
            repository.getRound(level, round)
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            round = data.round,
                            sentence = data.sentence,
                            maskedSentence = data.maskedSentence,
                            referenceAudioUrl = data.referenceAudioUrl
                        )
                    }
                }.onFailure { e ->
                    _effect.send(GamePlayEffect.ShowError(e.message ?: "라운드 정보를 불러올 수 없어요."))
                }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _state.update { it.copy(isRecording = true) }
            try {
                recordingFile = audioRecorder.start()
            } catch (e: Exception) {
                _state.update { it.copy(isRecording = false) }
                _effect.send(GamePlayEffect.ShowError("마이크 권한을 확인해주세요."))
            }
        }
    }

    private fun stopRecording() {
        val file = audioRecorder.stop()
        recordingFile = file
        _state.update { it.copy(isRecording = false, isAnalyzing = true) }

        val currentState = _state.value
        viewModelScope.launch {
            if (file == null) {
                _state.update { it.copy(isAnalyzing = false) }
                return@launch
            }
            repository.evaluate(currentState.level, currentState.round, file)
                .onSuccess { result ->
                    val mood = when (result.hearts) {
                        1 -> EngmuMood.INTERESTED
                        2 -> EngmuMood.EXCITED
                        3 -> EngmuMood.LOVE
                        else -> EngmuMood.NEUTRAL
                    }
                    if (result.gameOver) {
                        _state.update { it.copy(isAnalyzing = false, hearts = result.hearts, engmuMood = mood) }
                        if (result.hearts == 0) soundManager.playGameFail()
                        if (result.finalResult != null) {
                            _effect.send(GamePlayEffect.NavigateToResult(result.finalResult, result.hearts, prevBest))
                        } else {
                            _effect.send(GamePlayEffect.NavigateToResult(
                                GameFinalResult(
                                    finalScore = 0.0,
                                    hearts = 0,
                                    avgTotalScore = result.totalScore,
                                    avgWordRhythmScore = 0.0,
                                    avgDynamicStressScore = 0.0,
                                    avgWordAccuracy = 0.0
                                ),
                                hearts = 0,
                                prevBest = prevBest
                            ))
                        }
                    } else {
                        val heartGained = result.hearts > currentState.hearts
                        val nextRound = result.round + 1

                        if (heartGained) soundManager.playHeartGain()  // 추가

                        _state.update {
                            it.copy(
                                isAnalyzing = false,
                                hearts = result.hearts,
                                currentScore = result.totalScore,
                                engmuMood = mood,
                                showRoundModal = true,
                                lastRoundScore = result.totalScore,
                                lastRoundHeartGained = heartGained
                            )
                        }
                        viewModelScope.launch {
                            delay(2000)
                            _state.update { it.copy(showRoundModal = false, round = nextRound) }
                            loadRound(currentState.level, nextRound)
                        }
                    }
                }.onFailure { e ->
                    _state.update { it.copy(isAnalyzing = false) }
                    when {
                        e.message?.contains("음성이 인식되지 않았습니다") == true -> {
                            _state.update { it.copy(showVoiceNotRecognizedModal = true) }
                            soundManager.playError()
                        }
                        else -> {
                            _effect.send(GamePlayEffect.ShowError(e.message ?: "평가 중 오류가 발생했어요."))
                        }
                    }
                }
        }
    }


    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }
}