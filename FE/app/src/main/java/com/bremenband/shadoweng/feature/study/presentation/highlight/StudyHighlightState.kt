package com.bremenband.shadoweng.feature.study.presentation.highlight

import com.bremenband.shadoweng.core.ui.component.model.Annotation
import com.bremenband.shadoweng.core.ui.component.model.ExpressionInfo
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult

data class StudyHighlightUiState(
    val sessionId: Long = 0L,
    val sentence: String = "",
    val sentenceId: Long = 0L,
    val isBookmarked: Boolean = false,
    val embedUrl: String = "",
    val thumbnailUrl: String = "",
    val startSec: Double = 0.0,
    val endSec: Double = 0.0,
    val annotations: List<Annotation> = emptyList(),
    val selectedWord: String? = null,
    val expressionInfo: ExpressionInfo? = null,
    val isLoadingExpression: Boolean = false,
    val error: String? = null,
    val showKoreanSubtitle: Boolean = false,
    val koreanTranslation: String = "",
    val evaluationResult: EvaluationResult? = null,
)

sealed class StudyHighlightEvent {
    data class TapWord(val word: String, val charIndex: Int) : StudyHighlightEvent()
    object DismissExpression : StudyHighlightEvent()
    object RetryRecording : StudyHighlightEvent()
    object NextMode : StudyHighlightEvent()
    object SessionEnd : StudyHighlightEvent()
    object ToggleKoreanSubtitle : StudyHighlightEvent()
    object ToggleBookmark : StudyHighlightEvent()
}