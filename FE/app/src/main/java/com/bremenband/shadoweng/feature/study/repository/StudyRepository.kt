package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession

interface StudyRepository {
    suspend fun getSession(sessionId: Long): Result<StudySession>
    suspend fun createReport(sessionId: Long): Result<StudyReport>
    suspend fun getReport(sessionId: Long): Result<StudyReport>
}