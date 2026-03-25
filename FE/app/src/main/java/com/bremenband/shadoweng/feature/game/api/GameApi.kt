package com.bremenband.shadoweng.feature.game.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.game.api.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface GameApi {

    @GET("game/today")
    suspend fun getToday(): ApiResponse<GameTodayResponse>

    @GET("game/levels/{level}/rounds/{round}")
    suspend fun getRound(
        @Path("level") level: Int,
        @Path("round") round: Int
    ): ApiResponse<GameRoundResponse>

    @Multipart
    @POST("game/levels/{level}/rounds/{round}/evaluate")
    suspend fun evaluate(
        @Path("level") level: Int,
        @Path("round") round: Int,
        @Part file: MultipartBody.Part
    ): ApiResponse<GameEvaluateResponse>

    @GET("game/leaderboard")
    suspend fun getLeaderboard(): ApiResponse<GameLeaderboardResponse>

    @GET("game/profile")
    suspend fun getProfile(): ApiResponse<GameProfileResponse>
}