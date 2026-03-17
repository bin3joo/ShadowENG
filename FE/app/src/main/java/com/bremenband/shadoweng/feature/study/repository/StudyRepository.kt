package com.bremenband.shadoweng.feature.study.repository

import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import com.bremenband.shadoweng.feature.study.domain.StudyReport
import com.bremenband.shadoweng.feature.study.domain.StudySession

interface StudyRepository {
    suspend fun getSession(sessionId: Long): Result<StudySession>

    suspend fun getSentence(sessionId: Long, sentenceId: Long, step: Int): Result<SentenceItem>
    suspend fun createReport(sessionId: Long): Result<StudyReport>
    suspend fun getReport(sessionId: Long): Result<StudyReport>
}