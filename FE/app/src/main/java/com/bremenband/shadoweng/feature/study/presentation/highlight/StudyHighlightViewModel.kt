package com.bremenband.shadoweng.feature.study.presentation.highlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.AnnotationType
import com.bremenband.shadoweng.core.ui.component.model.ExpressionInfo
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
class StudyHighlightViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StudyHighlightUiState())
    val uiState: StateFlow<StudyHighlightUiState> = _uiState.asStateFlow()
    val navigateToNextMode = MutableSharedFlow<Unit>()
    val navigateRetry = MutableSharedFlow<Unit>()

    fun init(sentence: String) {
        _uiState.update {
            it.copy(
                sentence = sentence,
                annotations = getMockAnnotations(sentence)
            )
        }
    }

    fun onEvent(event: StudyHighlightEvent) {
        when (event) {
            is StudyHighlightEvent.ToggleKoreanSubtitle ->
                _uiState.update { it.copy(showKoreanSubtitle = !it.showKoreanSubtitle) }
            is StudyHighlightEvent.DismissExpression ->
                _uiState.update { it.copy(selectedWord = null, expressionInfo = null) }
            is StudyHighlightEvent.RetryRecording ->
                viewModelScope.launch { navigateRetry.emit(Unit) }
            is StudyHighlightEvent.NextMode ->
                viewModelScope.launch { navigateToNextMode.emit(Unit) }
            is StudyHighlightEvent.TapWord -> { }
        }
    }

    private fun loadExpressionInfo(word: String) {
        _uiState.update { it.copy(selectedWord = word, isLoadingExpression = true) }
        viewModelScope.launch {
            delay(800)
            _uiState.update {
                it.copy(isLoadingExpression = false, expressionInfo = getMockExpressionInfo(word))
            }
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

    private fun getMockExpressionInfo(word: String) = ExpressionInfo(
        word = word,
        pronunciation = "[${word.lowercase()}]",
        description = "LLM 설명이 여기에 표시됩니다.",
        examples = listOf("Example sentence using $word.")
    )
}