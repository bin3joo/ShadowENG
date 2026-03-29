package com.bremenband.shadoweng.feature.auth.api.dto

data class GuestLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val isNew: Boolean = false
)
data class GuestLoginRequest(val deviceId: String)
data class RefreshTokenRequest(
    val refreshToken: String
)