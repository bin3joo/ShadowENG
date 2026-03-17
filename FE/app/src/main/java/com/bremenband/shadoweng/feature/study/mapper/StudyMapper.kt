package com.bremenband.shadoweng.feature.study.mapper

import com.bremenband.shadoweng.feature.study.api.dto.EvaluationResponse
import com.bremenband.shadoweng.feature.study.api.dto.EvaluationScores
import com.bremenband.shadoweng.feature.study.api.dto.ReportResponse
import com.bremenband.shadoweng.feature.study.api.dto.SentenceData
import com.bremenband.shadoweng.feature.study.api.dto.SessionResponse
import com.bremenband.shadoweng.feature.study.domain.DifficultSentence
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import com.bremenband.shadoweng.feature.study.domain.EvaluationScores as ModelEvaluationScores
import com.bremenband.shadoweng.feature.study.domain.FeedbackItem
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession
import com.bremenband.shadoweng.feature.study.domain.WordFeedback

fun SessionResponse.toDomain() = StudySession(
    sessionId = sessionId,
    videoId = videoData.videoId,
    embedUrl = videoData.embedUrl,
    title = videoData.title,
    thumbnailUrl = videoData.thumbnailUrl,
    sentences = sentencesData.map { it.toDomain() }
)

fun SentenceData.toDomain() = SentenceItem(
    id = sentenceId,
    timestamp = "${startSec}s",
    content = sentence
)

fun EvaluationResponse.toDomain() = EvaluationResult(
    sentenceId = sentenceId,
    userTranscription = userTranscription,
    wordFeedback = details.wordLevelFeedback.map { WordFeedback(it.word, it.status) },
    boundaryToneFeedback = FeedbackItem(
        details.boundaryToneFeedback.status,
        details.boundaryToneFeedback.message
    ),
    dynamicStressFeedback = FeedbackItem(
        details.dynamicStressFeedback.status,
        details.dynamicStressFeedback.message
    ),
    scores = scores.toDomain()
)

fun EvaluationScores.toDomain() = ModelEvaluationScores(
    totalScore = totalScore,
    wordAccuracy = wordAccuracy,
    prosodyAndStress = prosodyAndStress,
    wordRhythmScore = wordRhythmScore,
    boundaryToneScore = boundaryToneScore,
    dynamicStressScore = dynamicStressScore,
    speedSimilarity = speedSimilarity,
    pauseSimilarity = pauseSimilarity
)

fun ReportResponse.toDomain() = StudyReport(
    sessionId = sessionId,
    totalScore = scores.totalScore.toInt(),
    speedScore = scores.speedSimilarity.toInt(),         // TODO: 매핑 기준 백엔드 확인
    stressScore = scores.dynamicStressScore.toInt(),     // TODO: 매핑 기준 백엔드 확인
    intonationScore = scores.boundaryToneScore.toInt(),  // TODO: 매핑 기준 백엔드 확인
    studyDuration = "",    // TODO: 백엔드 응답 필드 확인
    sentenceCount = difficultSentences.size, // TODO: 백엔드 전체 문장 수 필드 확인
    difficultSentences = difficultSentences.map {
        DifficultSentence(
            sentenceId = it.sentenceId,
            content = it.sentence,
            feedback = ""  // TODO: 백엔드 feedback 필드 확인
        )
    }
)