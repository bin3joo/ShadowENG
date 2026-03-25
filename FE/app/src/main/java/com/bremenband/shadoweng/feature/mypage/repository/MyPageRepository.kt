package com.bremenband.shadoweng.feature.mypage.repository

import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence
import com.bremenband.shadoweng.feature.mypage.domain.model.MyPageProfile
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary

interface MyPageRepository {
    suspend fun getProfile(): Result<MyPageProfile>
    suspend fun getSessions(): Result<List<SessionSummary>>
    suspend fun deleteSession(sessionId: Long): Result<Unit>
    suspend fun getBookmarks(): Result<List<BookmarkedSentence>>
    suspend fun getDailyCount(): Result<Int>
    suspend fun toggleBookmark(sentenceId: Long, isBookmarked: Boolean): Result<Boolean>  // 추가
}