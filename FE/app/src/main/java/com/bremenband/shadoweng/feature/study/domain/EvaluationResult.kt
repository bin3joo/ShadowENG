package com.bremenband.shadoweng.feature.study.domain

data class EvaluationResult(
    val sentenceId: Long,
    val userTranscription: String,
    val wordFeedback: List<WordFeedback>,
    val boundaryToneFeedback: FeedbackItem,
    val dynamicStressFeedback: FeedbackItem,
    val scores: EvaluationScores
)

data class WordFeedback(val word: String, val status: String)
data class FeedbackItem(val status: String, val message: String)

data class EvaluationScores(
    val totalScore: Double,
    val wordAccuracy: Double,
    val prosodyAndStress: Double,
    val wordRhythmScore: Double,
    val boundaryToneScore: Double,
    val dynamicStressScore: Double,
    val speedSimilarity: Double,
    val pauseSimilarity: Double
)