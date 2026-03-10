package com.bremenband.shadoweng.core.network

import com.bremenband.shadoweng.core.network.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    @POST("auth/login/guest")
    suspend fun guestLogin(): ApiResponse<GuestLoginResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @GET("users/me")
    suspend fun getMe(): ApiResponse<UserResponse>

    @GET("youtube")
    suspend fun getVideo(@Query("url") url: String): ApiResponse<VideoResponse>

    @POST("study-sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): ApiResponse<SessionResponse>

    @GET("study-sessions")
    suspend fun getSessions(): ApiResponse<ActiveSessionsResponse>

    @GET("study-sessions/recent")
    suspend fun getRecentSession(): ApiResponse<RecentSessionResponse>

    @GET("study-sessions/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: Long): ApiResponse<SessionResponse>

    @Multipart
    @POST("study-sessions/{sessionId}/evaluations")
    suspend fun createEvaluation(
        @Path("sessionId") sessionId: Long,
        @Part file: MultipartBody.Part
    ): ApiResponse<EvaluationResponse>

    @POST("study-sessions/{sessionId}/reports")
    suspend fun createReport(
        @Path("sessionId") sessionId: Long,
        @Body request: CreateReportRequest
    ): ApiResponse<ReportResponse>

    @GET("study-sessions/{sessionId}/reports")
    suspend fun getReport(@Path("sessionId") sessionId: Long): ApiResponse<ReportResponse>

    @GET("reports/daily")
    suspend fun getDailyReport(): ApiResponse<DailyReportResponse>

    @GET("bookmarks")
    suspend fun getBookmarks(): ApiResponse<BookmarkListResponse>

    @PATCH("sentences/{sentenceId}")
    suspend fun toggleBookmark(
        @Path("sentenceId") sentenceId: Long,
        @Body request: ToggleBookmarkRequest
    ): ApiResponse<BookmarkResponse>
}