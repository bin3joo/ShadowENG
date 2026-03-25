package com.bremenband.shadoweng.feature.study.mapper

import com.bremenband.shadoweng.feature.study.api.dto.EvaluationResponse
import com.bremenband.shadoweng.feature.study.api.dto.EvaluationScores
import com.bremenband.shadoweng.feature.study.api.dto.ReportResponse
import com.bremenband.shadoweng.feature.study.api.dto.SentenceData
import com.bremenband.shadoweng.feature.study.api.dto.SentenceDetailResponse
import com.bremenband.shadoweng.feature.study.api.dto.SessionResponse
import com.bremenband.shadoweng.feature.study.api.dto.WordFeedback
import com.bremenband.shadoweng.feature.study.domain.DifficultSentence
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import com.bremenband.shadoweng.feature.study.domain.EvaluationScores as ModelEvaluationScores
import com.bremenband.shadoweng.feature.study.domain.FeedbackItem
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession
import com.bremenband.shadoweng.feature.study.domain.WordFeedback as DomainWordFeedback

fun SessionResponse.toDomain() = StudySession(
    sessionId = sessionId,
    videoId = videoData.videoId,
    embedUrl = videoData.embedUrl,
    watchUrl = videoData.watchUrl,
    title = videoData.title,
    thumbnailUrl = videoData.thumbnailUrl,
    sentences = sentencesData
        .sortedBy { it.sentenceIndex }
        .map { it.toDomain() }
)

fun SentenceData.toDomain() = SentenceItem(
    id = sentenceId,
    timestamp = formatTimestamp(startSec),
    content = sentence,
    isCompleted = studyCount > 0,  // 추가
    startSec = startSec,
    endSec = endSec
)

private fun formatTimestamp(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

fun SentenceDetailResponse.toDomain() = SentenceItem(
    id = sentenceId,
    timestamp = "${startSec}s",
    content = sentence,
    startSec = startSec,
    endSec = endSec
)

fun EvaluationResponse.toDomain() = EvaluationResult(
    sentenceId = sentenceId,
    userTranscription = userTranscription,
    wordFeedback = details.wordLevelFeedback.map { DomainWordFeedback(it.word, it.status) },
    boundaryToneFeedback = FeedbackItem(
        details.boundaryToneFeedback.status,
        details.boundaryToneFeedback.message ?: ""
    ),
    dynamicStressFeedback = FeedbackItem(
        details.dynamicStressFeedback.status,
        details.dynamicStressFeedback.message ?: ""
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
    reportId = reportId,
    sessionId = sessionId,
    totalScore = scores.totalScore.toInt(),
    speedScore = scores.speedSimilarity.toInt(),
    stressScore = scores.dynamicStressScore.toInt(),
    intonationScore = scores.boundaryToneScore.toInt(),
    studyDuration = "",
    sentenceCount = difficultSentences.size,
    difficultSentences = difficultSentences.map {
        DifficultSentence(
            sentenceId = it.sentenceId,
            content = it.sentence,
            averageScore = it.averageScore,
            feedback = buildFeedback(it.boundaryToneStatus, it.dynamicStressStatus, it.wordFeedback)
        )
    }
)

private fun buildFeedback(
    boundaryTone: String,
    dynamicStress: String,
    wordFeedback: List<WordFeedback>
): String {
    val missedWords = wordFeedback.filter { it.status == "missed" }.map { it.word }
    val rushedWords = wordFeedback.filter { it.status == "rushed" }.map { it.word }
    val parts = mutableListOf<String>()
    when {
        missedWords.isNotEmpty() -> parts.add("\"${missedWords.take(2).joinToString(", ")}\" 발음을 더 명확하게 해보세요")
        rushedWords.isNotEmpty() -> parts.add("빠르기에 유의해서 말해보세요")
    }
    if (boundaryTone == "weak" || boundaryTone == "short") parts.add("문장 끝을 올려서 마무리해요")
    if (dynamicStress == "monotone") parts.add("강약을 살려서 말해보세요")
    return parts.firstOrNull() ?: "잘 말했어요!"
}