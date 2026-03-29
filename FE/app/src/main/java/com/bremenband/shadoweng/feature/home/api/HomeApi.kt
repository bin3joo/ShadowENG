package com.bremenband.shadoweng.feature.home.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.home.api.dto.DashboardResponse
import com.bremenband.shadoweng.feature.home.api.dto.UserMeResponse
import retrofit2.http.GET

interface HomeApi {
    @GET("users/me")
    suspend fun getUserMe(): UserMeResponse

    @GET("users/dashboard")
    suspend fun getDashboard(): ApiResponse<DashboardResponse>
}