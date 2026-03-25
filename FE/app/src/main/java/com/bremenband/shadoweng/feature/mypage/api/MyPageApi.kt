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

    @GET("bookmarks")
    suspend fun getBookmarks(): ApiResponse<BookmarkListResponse>

    @PATCH("bookmarks/{sentenceId}")
    suspend fun toggleBookmark(
        @Path("sentenceId") sentenceId: Long,
        @Body request: ToggleBookmarkRequest
    ): ApiResponse<BookmarkResponse>

    @GET("reports/daily")
    suspend fun getDailyReport(): ApiResponse<DailyReportResponse>


}