package com.bremenband.shadoweng.feature.mypage.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.mypage.api.MyPageApi
import com.bremenband.shadoweng.feature.mypage.presentation.LearningContent
import com.bremenband.shadoweng.feature.mypage.presentation.BookmarkItem
import com.bremenband.shadoweng.feature.mypage.mapper.toLearningContent
import com.bremenband.shadoweng.feature.mypage.mapper.toDomain
import com.bremenband.shadoweng.feature.mypage.mapper.toDailyCount

import javax.inject.Inject

class MyPageRepositoryImpl @Inject constructor(
    private val api: MyPageApi
) : MyPageRepository {
    override suspend fun getSessions(): Result<List<LearningContent>> =
        runCatching { api.getSessions().data?.activeSessions?.map { it.toLearningContent() } ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getBookmarks(): Result<List<BookmarkItem>> =
        runCatching { api.getBookmarks().data?.bookmarks?.map { it.toDomain() } ?: throw DomainException.NotFound }
            .mapDomainException()

    override suspend fun getDailyCount(): Result<Int> =
        runCatching { api.getDailyReport().data?.toDailyCount() ?: throw DomainException.NotFound }
            .mapDomainException()
}