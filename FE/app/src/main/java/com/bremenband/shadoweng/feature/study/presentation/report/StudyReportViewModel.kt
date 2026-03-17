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

    fun loadReport(sessionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // TODO: 백엔드 연동 후 주석 해제
            // repository.getReport(sessionId)
            //     .onSuccess { report -> _uiState.update { it.copy(isLoading = false, report = report) } }
            //     .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    report = com.bremenband.shadoweng.feature.study.domain.StudyReport(
                        sessionId = sessionId,
                        totalScore = 78,
                        speedScore = 82,
                        stressScore = 75,
                        intonationScore = 88,
                        studyDuration = "1시간 3분",
                        sentenceCount = 12,
                        difficultSentences = listOf(
                            com.bremenband.shadoweng.feature.study.domain.DifficultSentence(
                                sentenceId = 1L,
                                content = "I had this meeting with a big studio Hollywood casting director.",
                                feedback = "빠르기에 유의해서 말해보세요."
                            ),
                            com.bremenband.shadoweng.feature.study.domain.DifficultSentence(
                                sentenceId = 2L,
                                content = "Everybody knows you are a beautiful, talented black girl.",
                                feedback = "세기에 유의해서 말해보세요."
                            ),
                            com.bremenband.shadoweng.feature.study.domain.DifficultSentence(
                                sentenceId = 3L,
                                content = "You're knocking boots with the chicky babes.",
                                feedback = "높낮이에 유의해서 말해보세요."
                            )
                        )
                    )
                )
            }
        }
    }
}