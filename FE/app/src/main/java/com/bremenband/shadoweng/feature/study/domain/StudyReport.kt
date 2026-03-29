package com.bremenband.shadoweng.feature.study.domain

data class StudyReport(
    val reportId: Long,
    val sessionId: Long,
    val totalScore: Int,
    val speedScore: Int,
    val stressScore: Int,
    val intonationScore: Int,
    val studyDuration: String,
    val sentenceCount: Int,
    val difficultSentences: List<DifficultSentence>
)

data class DifficultSentence(
    val sentenceId: Long,
    val content: String,
    val feedback: String,
    val averageScore: Double = 0.0,
    val wordFeedback: List<WordFeedback> = emptyList()
)

// data class WordFeedback(val word: String, val status: String) ← 이 줄 삭제