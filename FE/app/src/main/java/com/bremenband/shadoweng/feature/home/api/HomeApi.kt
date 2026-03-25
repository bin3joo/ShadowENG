package com.bremenband.shadoweng.feature.home.api

import com.bremenband.shadoweng.feature.home.api.dto.UserMeResponse
import retrofit2.http.GET

interface HomeApi {
    @GET("users/me")
    suspend fun getUserMe(): UserMeResponse
}