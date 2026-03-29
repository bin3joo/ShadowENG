package com.bremenband.shadoweng.feature.study.presentation.learning

import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer

enum class SubtitleMode { NONE, FULL, PARTIAL, NONE_FINAL }

data class StudyLearningUiState(
    val sentence: SentenceItem? = null,
    val embedUrl: String = "",
    val thumbnailUrl: String = "",
    val startSec: Double = 0.0,
    val endSec: Double = 0.0,
    val subtitleMode: SubtitleMode = SubtitleMode.NONE,
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val showEncourageModal: Boolean = false,
    val isNavigating: Boolean = false,
    val error: String? = null,
    val youtubePlayer: YouTubePlayer? = null,  // ← 추가
    val showVoiceNotRecognizedModal: Boolean = false,
    val showTooShortModal: Boolean = false
)

sealed class StudyLearningEvent {
    object StartRecording : StudyLearningEvent()
    object StopRecording : StudyLearningEvent()
    object RetryRecording : StudyLearningEvent()
    object DismissVoiceModal : StudyLearningEvent()
    object DismissTooShortModal : StudyLearningEvent()
}