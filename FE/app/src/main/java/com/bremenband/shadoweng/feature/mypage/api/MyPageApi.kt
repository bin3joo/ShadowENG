package com.bremenband.shadoweng.feature.mypage.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.*
import com.bremenband.shadoweng.feature.study.api.dto.ActiveSessionsResponse
import retrofit2.http.*

interface MyPageApi {
    @GET("users/me")
    suspend fun getMe(): ApiResponse<UserResponse>

    @GET("study-sessions")
    suspend fun getSessions(): ApiResponse<ActiveSessionsDto>

    @DELETE("study-sessions/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: Long): ApiResponse<String>

    // 기존 문장 북마크 제거, 세션 북마크로 교체
    @PATCH("bookmarks/{sessionId}")
    suspend fun toggleSessionBookmark(
        @Path("sessionId") sessionId: Long,
        @Body request: ToggleBookmarkRequest
    ): ApiResponse<SessionBookmarkResponse>

    @GET("bookmarks")
    suspend fun getBookmarks(): ApiResponse<BookmarkListResponse>

    @GET("reports/daily")
    suspend fun getDailyReport(): ApiResponse<DailyReportResponse>


}