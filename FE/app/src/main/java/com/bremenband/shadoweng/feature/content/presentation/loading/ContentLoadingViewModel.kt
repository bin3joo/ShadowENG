package com.bremenband.shadoweng.feature.content.presentation.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentLoadingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ContentLoadingUiState())
    val uiState: StateFlow<ContentLoadingUiState> = _uiState.asStateFlow()
    val navigateToStudy = MutableSharedFlow<Unit>()

    init { startAnalysis() }

    private fun startAnalysis() {
        viewModelScope.launch {
            val steps = ContentAnalysisStep.entries
            steps.forEachIndexed { index, step ->
                updateStep(step, ContentStepStatus.IN_PROGRESS)
                updateProgress(index / steps.size.toFloat())
                delay(1500) // TODO: 서버 분석 완료 이벤트로 교체
                updateStep(step, ContentStepStatus.DONE)
                updateProgress((index + 1) / steps.size.toFloat())
                delay(300)
            }
            _uiState.update { it.copy(isDone = true) }
            delay(400)
            navigateToStudy.emit(Unit)
        }
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