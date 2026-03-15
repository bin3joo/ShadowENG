package com.bremenband.shadoweng.feature.content.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.content.api.dto.CreateSessionRequest
import com.bremenband.shadoweng.feature.content.api.dto.VideoResponse
import com.bremenband.shadoweng.feature.study.api.dto.SessionResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ContentApi {
    @GET("youtube")
    suspend fun getVideo(@Query("url") url: String): ApiResponse<VideoResponse>

    @POST("study-sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): ApiResponse<SessionResponse>
}