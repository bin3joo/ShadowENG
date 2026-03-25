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

    override suspend fun getBookmarks(): Result<List<BookmarkedSentence>> =
        runCatching { api.getBookmarks().data?.bookmarks?.map { it.toDomain() } ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getDailyCount(): Result<Int> =
        runCatching { api.getDailyReport().data?.toDailyCount() ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun toggleBookmark(sentenceId: Long, isBookmarked: Boolean): Result<Boolean> =
        runCatching {
            api.toggleBookmark(sentenceId, ToggleBookmarkRequest(isBookmarked)).data?.isBookmarked
                ?: throw DomainException.NotFound
        }.mapDomainException()
}