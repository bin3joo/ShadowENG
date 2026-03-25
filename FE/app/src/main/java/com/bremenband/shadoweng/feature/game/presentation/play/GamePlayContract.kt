package com.bremenband.shadoweng.feature.game.presentation.play

import com.bremenband.shadoweng.feature.game.domain.model.GameFinalResult

// MVI Contract
data class GamePlayState(
    val level: Int = 1,
    val round: Int = 1,
    val hearts: Int = 0,
    val currentScore: Double = 0.0,
    val sentence: String? = null,
    val maskedSentence: String? = null,
    val referenceAudioUrl: String = "",
    val engmuMood: EngmuMood = EngmuMood.NEUTRAL,
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val countdown: Int? = null,
    val error: String? = null,
    val showRoundModal: Boolean = false,
    val lastRoundScore: Double = 0.0,
    val lastRoundHeartGained: Boolean = false,
    val showVoiceNotRecognizedModal: Boolean = false
)

enum class EngmuMood { NEUTRAL, INTERESTED, EXCITED, LOVE }

sealed class GamePlayIntent {
    object StartCountdown : GamePlayIntent()
    object StopRecording : GamePlayIntent()
    object RetryCountdown : GamePlayIntent()
    data class SetAudioPlaying(val isPlaying: Boolean) : GamePlayIntent()
    object DismissVoiceModal : GamePlayIntent()
}

sealed class GamePlayEffect {
    data class NavigateToResult(
        val finalResult: GameFinalResult,
        val hearts: Int,
        val prevBest: Double = 0.0
    ) : GamePlayEffect()
    object NavigateToLeaderboard : GamePlayEffect()
    data class ShowError(val message: String) : GamePlayEffect()
    object ShowVoiceNotRecognized : GamePlayEffect()
}

