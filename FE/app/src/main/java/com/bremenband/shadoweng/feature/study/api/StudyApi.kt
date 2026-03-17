package com.bremenband.shadoweng.feature.study.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.mypage.api.dto.DailyReportResponse
import com.bremenband.shadoweng.feature.study.api.dto.*
import retrofit2.http.*

interface StudyApi {
    @GET("study-sessions")
    suspend fun getSessions(): ApiResponse<ActiveSessionsResponse>

    @GET("study-sessions/recent")
    suspend fun getRecentSession(): ApiResponse<RecentSessionResponse>

    @GET("study-sessions/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: Long): ApiResponse<SessionResponse>

    @POST("study-sessions/{sessionId}/evaluations")
    suspend fun createEvaluation(
        @Path("sessionId") sessionId: Long,
        @Query("sentenceId") sentenceId: Long,
        @Body request: EvaluationRequest
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


}