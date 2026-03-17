package com.bremenband.shadoweng.feature.study.api.dto

data class SessionResponse(
    val sessionId: Long,
    val videoData: VideoData,
    val sentencesData: List<SentenceData>
)
data class VideoData(
    val videoId: String,
    val embedUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int,
    val channelTitle: String
)
data class SentenceData(
    val sentenceId: Long,
    val sentence: String,
    val startSec: Double,
    val endSec: Double,
    val durationSec: Double,
    val studyCount: Int
)

data class SentenceDetailResponse(
    val step: Int,
    val sessionId: Long,
    val sentenceId: Long,
    val sentence: String,
    val hiddenSentence: String?,
    val startSec: Double,
    val endSec: Double,
    val durationSec: Double,
    val studyCount: Int
)

data class LatestActiveSession(val sessionId: Long, val thumbnailUrl: String, val progressRate: Int)
data class ActiveSessionsResponse(val activeSessions: List<ActiveSession>)
data class ActiveSession(val sessionId: Long, val thumbnailUrl: String, val progressRate: Int)

data class EvaluationRequest(val file: String)
data class EvaluationResponse(
    val sentenceId: Long,
    val startSec: Double,
    val endSec: Double,
    val durationSec: Double,
    val userTranscription: String,
    val details: EvaluationDetails,
    val scores: EvaluationScores
)
data class EvaluationDetails(
    val wordLevelFeedback: List<WordFeedback>,
    val boundaryToneFeedback: ToneFeedback,
    val dynamicStressFeedback: ToneFeedback
)
data class WordFeedback(val word: String, val status: String)
data class ToneFeedback(val status: String, val message: String)
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

data class CreateReportRequest(val sessionId: Long)
data class ReportResponse(
    val sessionId: Long,
    val scores: EvaluationScores,
    val difficultSentences: List<DifficultSentence>
)
data class DifficultSentence(val sentenceId: Long, val sentence: String)

data class RecentSessionResponse(
    val latestActiveSession: LatestActiveSessionDto?
)

data class LatestActiveSessionDto(
    val sessionId: Long,
    val thumbnails: ThumbnailsDto?,
    val progressRate: Int
)

data class ThumbnailsDto(
    val standard: ThumbnailDto?
)

data class ThumbnailDto(
    val url: String,
    val width: Int,
    val height: Int
)