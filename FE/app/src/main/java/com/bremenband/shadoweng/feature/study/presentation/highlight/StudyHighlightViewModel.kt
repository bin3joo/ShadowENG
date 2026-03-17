package com.bremenband.shadoweng.feature.study.presentation.highlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.AnnotationType
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
class StudyHighlightViewModel @Inject constructor(
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyHighlightUiState())
    val uiState: StateFlow<StudyHighlightUiState> = _uiState.asStateFlow()

    private val _navigateToNextMode = MutableSharedFlow<Unit>()
    val navigateToNextMode: SharedFlow<Unit> = _navigateToNextMode.asSharedFlow()

    private val _navigateRetry = MutableSharedFlow<Unit>()
    val navigateRetry: SharedFlow<Unit> = _navigateRetry.asSharedFlow()

    fun init(sessionId: Long, sentenceId: Long) {
        // TODO: step 값은 현재 학습 단계에 따라 동적으로 결정 필요 (BE 협의)
        val step = 2
        viewModelScope.launch {
            studyRepository.getSentence(sessionId, sentenceId, step)
                .onSuccess { sentence: SentenceItem ->
                    _uiState.update {
                        it.copy(
                            sentence = sentence.content,
                            annotations = getMockAnnotations(sentence.content)
                        )
                    }
                }
                .onFailure { e: Throwable ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun onEvent(event: StudyHighlightEvent) {
        when (event) {
            is StudyHighlightEvent.ToggleKoreanSubtitle ->
                _uiState.update { it.copy(showKoreanSubtitle = !it.showKoreanSubtitle) }
            is StudyHighlightEvent.DismissExpression ->
                _uiState.update { it.copy(selectedWord = null, expressionInfo = null) }
            is StudyHighlightEvent.RetryRecording ->
                viewModelScope.launch { _navigateRetry.emit(Unit) }
            is StudyHighlightEvent.NextMode ->
                viewModelScope.launch { _navigateToNextMode.emit(Unit) }
            is StudyHighlightEvent.TapWord -> { }
        }
    }

    private fun getMockAnnotations(sentence: String): List<Annotation> {
        if (sentence.length < 10) return emptyList()
        return listOf(
            Annotation(0, 3, AnnotationType.HIGHLIGHT, 0xFFFFEB3B),
            Annotation(4, 11, AnnotationType.BOLD),
            Annotation(4, 11, AnnotationType.ARROW_UP),
            Annotation(16, 22, AnnotationType.UNDERLINE),
        )
    }
}