package com.bremenband.shadoweng.feature.auth.api.dto

data class GuestLoginResponse(val token: String, val userId: Long, val nickname: String)
data class RefreshTokenRequest(val refreshToken: String)