package com.bremenband.shadoweng.feature.auth.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.auth.api.dto.GuestLoginResponse
import com.bremenband.shadoweng.feature.auth.api.dto.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login/guest")
    suspend fun guestLogin(): ApiResponse<GuestLoginResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<GuestLoginResponse>
}