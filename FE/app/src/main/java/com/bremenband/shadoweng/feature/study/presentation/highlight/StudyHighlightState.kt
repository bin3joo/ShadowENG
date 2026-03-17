package com.bremenband.shadoweng.feature.study.presentation.highlight

import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.ExpressionInfo

data class StudyHighlightUiState(
    val sentence: String = "",
    val annotations: List<Annotation> = emptyList(),
    val selectedWord: String? = null,
    val expressionInfo: ExpressionInfo? = null,
    val isLoadingExpression: Boolean = false,
    val error: String? = null,
    val showKoreanSubtitle: Boolean = false,
    val koreanTranslation: String = "",
    val pronunciationFeedback: String = "",
    val expressionDescription: String = "",
)

sealed class StudyHighlightEvent {
    data class TapWord(val word: String, val charIndex: Int) : StudyHighlightEvent()
    object DismissExpression : StudyHighlightEvent()
    object RetryRecording : StudyHighlightEvent()
    object NextMode : StudyHighlightEvent()
    object ToggleKoreanSubtitle : StudyHighlightEvent()
}