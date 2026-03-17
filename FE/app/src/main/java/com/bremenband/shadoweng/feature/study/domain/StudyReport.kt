package com.bremenband.shadoweng.feature.study.domain

data class StudyReport(
    val sessionId: Long,
    val totalScore: Int,
    val speedScore: Int,        // TODO: EvaluationScores.speedSimilarity 매핑 확인
    val stressScore: Int,       // TODO: EvaluationScores.dynamicStressScore 매핑 확인
    val intonationScore: Int,   // TODO: EvaluationScores.boundaryToneScore 매핑 확인
    val studyDuration: String,  // TODO: 백엔드 응답 필드 확인
    val sentenceCount: Int,     // TODO: 백엔드 응답 필드 확인
    val difficultSentences: List<DifficultSentence>
)

data class DifficultSentence(
    val sentenceId: Long,
    val content: String,       // sentence → content
    val feedback: String       // TODO: 백엔드 feedback 필드 확인
)