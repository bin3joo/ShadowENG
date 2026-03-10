package com.bremenband.shadoweng.core.network.dto

data class ApiResponse<T>(
    val isSuccess: Boolean,
    val data: T?,
    val message: String?,
    val code: Int
)

data class GuestLoginResponse(val token: String, val userId: Long, val nickname: String)
data class UserResponse(val userId: Long, val email: String, val nickname: String, val visitedCount: Int)
data class VideoResponse(val videoId: String, val title: String, val thumbnailUrl: String?, val duration: Int, val channelTitle: String, val embedUrl: String)
data class CreateSessionRequest(val embedUrl: String, val startSec: Double, val endSec: Double)

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

data class RecentSessionResponse(
    val latestActiveSession: LatestActiveSession?
)

data class LatestActiveSession(
    val sessionId: Long,
    val thumbnailUrl: String,
    val progressRate: Int
)

data class ActiveSessionsResponse(
    val activeSessions: List<ActiveSession>
)

data class ActiveSession(
    val sessionId: Long,
    val thumbnailUrl: String,
    val progressRate: Int
)

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

data class DifficultSentence(
    val sentenceId: Long,
    val sentence: String
)

data class DailyReportResponse(
    val studyData: List<DailyStudyData>
)

data class DailyStudyData(
    val date: String,
    val studiedSentencesCount: Int
)

data class ToggleBookmarkRequest(val isBookmarked: Boolean)

data class BookmarkResponse(
    val sentenceId: Long,
    val sentence: String,
    val isBookmarked: Boolean
)

data class BookmarkListResponse(
    val bookmarks: List<BookmarkItemResponse>
)

data class BookmarkItemResponse(
    val sentenceId: Long,
    val sentence: String,
    val sessionId: Long
)