package com.bremenband.shadoweng.feature.auth.repository

interface AuthRepository {
    suspend fun devLogin(userId: Long = 1L): Result<Unit>
    // TODO: 게스트 로그인 활성화 시 추가
    // suspend fun guestLogin(): Result<Unit>
    suspend fun logout(): Result<Unit>
}