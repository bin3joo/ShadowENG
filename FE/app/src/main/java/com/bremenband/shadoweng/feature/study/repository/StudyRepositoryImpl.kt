package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.mypage.api.dto.ToggleBookmarkRequest
import com.bremenband.shadoweng.feature.study.api.StudyApi
import com.bremenband.shadoweng.feature.study.api.dto.SentenceDetailResponse
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
            try {
                val response = api.createEvaluation(sessionId, sentenceId, step, filePart)
                response.data?.toDomain()?.also { cachedEvaluation = it }
                    ?: throw DomainException.InvalidResponse
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val message = try {
                    org.json.JSONObject(errorBody ?: "").getString("message")
                } catch (ex: Exception) {
                    e.message() ?: "평가 중 오류가 발생했어요."
                }
                throw DomainException.Unknown(message)
            }
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

    override suspend fun getSentenceDetail(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceDetailResponse> =
        runCatching {
            api.getSentence(sessionId, sentenceId, step).data ?: throw DomainException.NotFound
        }.mapDomainException()

    override suspend fun startReview(sessionId: Long): Result<Unit> = runCatching {
        api.startReview(sessionId)
    }

    override suspend fun reLearn(sessionId: Long): Result<Long> = runCatching {
        api.reLearn(sessionId).data?.sessionId
            ?: error("세션 생성 실패")
    }
}