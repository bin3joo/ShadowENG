package com.bremenband.shadoweng.feature.study.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.BookmarkResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.DailyReportResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.ToggleBookmarkRequest
import com.bremenband.shadoweng.feature.study.api.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface StudyApi {
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
        @Query("sentenceId") sentenceId: Long,
        @Query("step") step: Int = 1,
        @Part file: MultipartBody.Part
    ): ApiResponse<EvaluationResponse>

    @GET("study-sessions/{sessionId}/sentences/{sentenceId}")
    suspend fun getSentence(
        @Path("sessionId") sessionId: Long,
        @Path("sentenceId") sentenceId: Long,
        @Query("step") step: Int
    ): ApiResponse<SentenceDetailResponse>

    @POST("study-sessions/{sessionId}/reports")
    suspend fun createReport(
        @Path("sessionId") sessionId: Long,
        @Body request: CreateReportRequest
    ): ApiResponse<ReportResponse>

    // 전체 조회
    @GET("study-sessions/{sessionId}/reports")
    suspend fun getReports(
        @Path("sessionId") sessionId: Long
    ): ApiResponse<List<ReportResponse>>

    // 단건 조회
    @GET("study-sessions/{sessionId}/reports/{reportId}")
    suspend fun getReport(
        @Path("sessionId") sessionId: Long,
        @Path("reportId") reportId: Long
    ): ApiResponse<ReportResponse>

    @GET("reports/daily")
    suspend fun getDailyReport(): ApiResponse<DailyReportResponse>

    @PATCH("bookmarks/{sentenceId}")
    suspend fun toggleBookmark(
        @Path("sentenceId") sentenceId: Long,
        @Body request: ToggleBookmarkRequest
    ): ApiResponse<BookmarkResponse>
}