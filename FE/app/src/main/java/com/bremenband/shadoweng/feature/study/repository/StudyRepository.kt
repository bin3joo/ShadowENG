package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.feature.study.api.dto.SentenceDetailResponse
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession
import java.io.File

interface StudyRepository {
    suspend fun getSession(sessionId: Long): Result<StudySession>
    suspend fun getSentence(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceItem>
    suspend fun createEvaluation(sessionId: Long, sentenceId: Long, step: Int, audioFile: File): Result<EvaluationResult>
    suspend fun getReports(sessionId: Long): Result<List<StudyReport>>        // 전체 조회
    suspend fun getReport(sessionId: Long, reportId: Long): Result<StudyReport> // 단건 조회
    fun getCachedEvaluation(): EvaluationResult?
    suspend fun toggleBookmark(sentenceId: Long, isBookmarked: Boolean): Result<Boolean>
    suspend fun getSentenceDetail(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceDetailResponse>
    suspend fun startReview(sessionId: Long): Result<Unit>
    suspend fun reLearn(sessionId: Long): Result<Long>  // 새 sessionId 반환
}