package com.bremenband.shadoweng.feature.mypage.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.mypage.api.MyPageApi
import com.bremenband.shadoweng.feature.mypage.api.dto.ToggleBookmarkRequest
import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence
import com.bremenband.shadoweng.feature.mypage.domain.model.MyPageProfile
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary
import com.bremenband.shadoweng.feature.mypage.mapper.toDailyCount
import com.bremenband.shadoweng.feature.mypage.mapper.toDomain
import javax.inject.Inject

class MyPageRepositoryImpl @Inject constructor(
    private val api: MyPageApi
) : MyPageRepository {

    override suspend fun getProfile(): Result<MyPageProfile> =
        runCatching { api.getMe().data?.toDomain() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getSessions(): Result<List<SessionSummary>> =
        runCatching {
            val data = api.getSessions().data ?: throw DomainException.NotFound
            val active = data.activeSessions.map { it.toDomain() }
            val completed = data.completedSessions.map { it.toDomain() }
            active + completed
        }.mapDomainException()

    override suspend fun deleteSession(sessionId: Long): Result<Unit> =
        runCatching { api.deleteSession(sessionId) }
            .mapDomainException()
            .map { }


    override suspend fun getDailyCount(): Result<Int> =
        runCatching { api.getDailyReport().data?.toDailyCount() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun toggleSessionBookmark(sessionId: Long, isBookmarked: Boolean): Result<Boolean> =
        runCatching {
            api.toggleSessionBookmark(sessionId, ToggleBookmarkRequest(isBookmarked)).data?.isBookmarked
                ?: throw DomainException.NotFound
        }

    override suspend fun getBookmarkedSessions(): Result<List<SessionSummary>> =
        runCatching {
            api.getBookmarks().data?.bookmarks?.map {
                SessionSummary(
                    sessionId = it.sessionId,
                    title = it.videoTitle ?: "",
                    thumbnailUrl = it.thumbnailUrl,
                    completedSentences = it.completedSentences,
                    totalSentences = it.totalSentences,
                    isBookmarked = true
                )
            } ?: emptyList()
        }
}