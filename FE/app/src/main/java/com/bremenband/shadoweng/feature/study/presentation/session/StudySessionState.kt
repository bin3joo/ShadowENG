package com.bremenband.shadoweng.feature.study.presentation.session

import com.bremenband.shadoweng.feature.study.domain.SentenceItem

data class StudySessionUiState(
    val title: String = "",
    val embedUrl: String = "",
    val thumbnailUrl: String = "",
    val sentences: List<SentenceItem> = emptyList(),
    val selectedSentenceId: Long? = null,
    val isLoading: Boolean = false
)

sealed class StudySessionEvent {
    data class SelectSentence(val sentenceId: Long) : StudySessionEvent()
    object StartStudy : StudySessionEvent()
}