package com.bremenband.shadoweng.feature.study.presentation.learning

import com.bremenband.shadoweng.feature.study.domain.SentenceItem

enum class SubtitleMode { NONE, FULL, PARTIAL, NONE_FINAL }

data class StudyLearningUiState(
    val sentence: SentenceItem? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.NONE,
    val countdown: Int? = null,
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val error: String? = null
)

sealed class StudyLearningEvent {
    object StartCountdown : StudyLearningEvent()
    object StopRecording : StudyLearningEvent()
    object RetryRecording : StudyLearningEvent()
}