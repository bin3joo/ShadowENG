package com.bremenband.shadoweng.feature.content.presentation.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.content.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
class ContentLoadingViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentLoadingUiState())
    val uiState: StateFlow<ContentLoadingUiState> = _uiState.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    fun init(embedUrl: String, startSec: Double, endSec: Double) {
        viewModelScope.launch {
            startTipRotation()
            repository.createSession(
                embedUrl = embedUrl,
                startSec = startSec,
                endSec = endSec
            ).onSuccess { sessionId ->
                // TODO: WebSocket 또는 중간 진행 상태 수신 연동 후 finishSteps() 활성화
                // finishSteps()
                delay(400)
                _navigateToStudy.emit(sessionId)
            }.onFailure {
                // TODO: 에러 UiState 처리
                _uiState.update { it.copy(isDone = true) }
            }
        }
    }

    private fun startTipRotation() {
        viewModelScope.launch {
            val shuffled = loadingTips.shuffled()
            var index = 0
            while (true) {
                _uiState.update { it.copy(currentTip = shuffled[index % shuffled.size]) }
                index++
                delay(7000) // 팁 전환 간격
            }
        }
    }

    private suspend fun finishSteps() {
        val steps = ContentAnalysisStep.entries
        steps.forEachIndexed { index, step ->
            updateStep(step, ContentStepStatus.IN_PROGRESS)
            updateProgress(index / steps.size.toFloat())
            delay(600)
            updateStep(step, ContentStepStatus.DONE)
            updateProgress((index + 1) / steps.size.toFloat())
            delay(200)
        }
        _uiState.update { it.copy(isDone = true) }
    }

    private fun updateStep(step: ContentAnalysisStep, status: ContentStepStatus) {
        _uiState.update { state ->
            state.copy(steps = state.steps.map {
                if (it.step == step) it.copy(status = status) else it
            })
        }
    }

    private fun updateProgress(value: Float) {
        _uiState.update { it.copy(progress = value) }
    }
}