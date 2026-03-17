package com.bremenband.shadoweng.feature.mypage.repository

import com.bremenband.shadoweng.feature.mypage.presentation.BookmarkItem
import com.bremenband.shadoweng.feature.mypage.presentation.LearningContent
interface MyPageRepository {
    suspend fun getSessions(): Result<List<LearningContent>>
    suspend fun getBookmarks(): Result<List<BookmarkItem>>
    suspend fun getDailyCount(): Result<Int>
}