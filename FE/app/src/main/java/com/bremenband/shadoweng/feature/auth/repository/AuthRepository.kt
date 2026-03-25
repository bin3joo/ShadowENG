package com.bremenband.shadoweng.feature.auth.repository

interface AuthRepository {
    suspend fun devLogin(userId: Long = 1L): Result<Unit>
    suspend fun guestLogin(): Result<Unit>
    suspend fun logout(): Result<Unit>
}