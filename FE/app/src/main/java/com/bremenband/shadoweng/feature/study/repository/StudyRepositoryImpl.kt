package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.study.api.StudyApi
import com.bremenband.shadoweng.feature.study.api.dto.CreateReportRequest
import com.bremenband.shadoweng.feature.study.mapper.toDomain
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudySession
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import javax.inject.Inject

class StudyRepositoryImpl @Inject constructor(
    private val api: StudyApi
) : StudyRepository {
    override suspend fun getSession(sessionId: Long): Result<StudySession> =
        runCatching { api.getSession(sessionId).data?.toDomain() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun createReport(sessionId: Long): Result<StudyReport> =
        runCatching { api.createReport(sessionId, CreateReportRequest(sessionId)).data?.toDomain() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getReport(sessionId: Long): Result<StudyReport> =
        runCatching { api.getReport(sessionId).data?.toDomain() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getSentence(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceItem> =
        runCatching {
            api.getSentence(sessionId, sentenceId, step).data?.toDomain()
                ?: throw DomainException.NotFound
        }.mapDomainException()
}