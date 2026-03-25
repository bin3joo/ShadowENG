package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.mypage.api.dto.ToggleBookmarkRequest
import com.bremenband.shadoweng.feature.study.api.StudyApi
import com.bremenband.shadoweng.feature.study.api.dto.CreateReportRequest
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession
import com.bremenband.shadoweng.feature.study.mapper.toDomain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class StudyRepositoryImpl @Inject constructor(
    private val api: StudyApi,
) : StudyRepository {

    private var cachedEvaluation: EvaluationResult? = null

    override fun getCachedEvaluation() = cachedEvaluation

    override suspend fun getSession(sessionId: Long): Result<StudySession> =
        runCatching {
            api.getSession(sessionId).data?.toDomain() ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun getSentence(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceItem> =
        runCatching {
            api.getSentence(sessionId, sentenceId, step).data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun createEvaluation(sessionId: Long, sentenceId: Long, step: Int, audioFile: File): Result<EvaluationResult> =
        runCatching {
            val filePart = MultipartBody.Part.createFormData(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            api.createEvaluation(sessionId, sentenceId, step, filePart)
                .data?.toDomain()?.also { cachedEvaluation = it }
                ?: throw DomainException.InvalidResponse
        }.mapDomainException()

    override suspend fun createReport(sessionId: Long): Result<StudyReport> =
        runCatching {
            api.createReport(sessionId, CreateReportRequest(sessionId)).data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun getReports(sessionId: Long): Result<List<StudyReport>> =
        runCatching {
            api.getReports(sessionId).data?.map { it.toDomain() }
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun getReport(sessionId: Long, reportId: Long): Result<StudyReport> =
        runCatching {
            api.getReport(sessionId, reportId).data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun toggleBookmark(sentenceId: Long, isBookmarked: Boolean): Result<Boolean> =
        runCatching {
            api.toggleBookmark(sentenceId, ToggleBookmarkRequest(isBookmarked)).data?.isBookmarked
                ?: throw DomainException.NotFound
        }.mapDomainException()
}