package com.bremenband.shadoweng.feature.study.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyReportViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyReportUiState())
    val uiState: StateFlow<StudyReportUiState> = _uiState.asStateFlow()

    fun loadReport(sessionId: Long, reportId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getReport(sessionId, reportId)
                .onSuccess { report ->
                    _uiState.update { it.copy(isLoading = false, report = report) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}