package com.bremenband.shadoweng.feature.auth.api

import com.bremenband.shadoweng.core.network.dto.ApiResponse
import com.bremenband.shadoweng.feature.auth.api.dto.GuestLoginResponse
import com.bremenband.shadoweng.feature.auth.api.dto.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login/dev")
    suspend fun devLogin(@Query("userId") userId: Long): ApiResponse<GuestLoginResponse>

    // TODO: 게스트 로그인 활성화 시 주석 해제
    // @POST("auth/login/guest")
    // suspend fun guestLogin(): ApiResponse<GuestLoginResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<GuestLoginResponse>
}