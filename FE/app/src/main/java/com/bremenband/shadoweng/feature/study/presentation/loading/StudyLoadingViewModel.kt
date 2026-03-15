package com.bremenband.shadoweng.feature.study.presentation.loading

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
class StudyLoadingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StudyLoadingUiState())
    val uiState: StateFlow<StudyLoadingUiState> = _uiState.asStateFlow()
    val navigateToHighlight = MutableSharedFlow<Unit>()
    val navigateToSession = MutableSharedFlow<Unit>()

    init { startAnalysis() }

    private fun startAnalysis() {
        viewModelScope.launch {
            val steps = AnalysisStep.entries
            steps.forEachIndexed { index, step ->
                updateStep(step, StepStatus.IN_PROGRESS)
                updateProgress(index / steps.size.toFloat())
                delay(1200)
                updateStep(step, StepStatus.DONE)
                updateProgress((index + 1) / steps.size.toFloat())
                delay(300)
            }
            _uiState.update { it.copy(isDone = true) }
            delay(400)
            navigateToSession.emit(Unit)
        }
    }

    fun updateStepFromServer(step: AnalysisStep, status: StepStatus) = updateStep(step, status)

    private fun updateStep(step: AnalysisStep, status: StepStatus) {
        _uiState.update { state ->
            state.copy(steps = state.steps.map { if (it.step == step) it.copy(status = status) else it })
        }
    }

    private fun updateProgress(value: Float) {
        _uiState.update { it.copy(progress = value) }
    }
}