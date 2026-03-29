package com.bremenband.shadoweng.feature.auth.repository

interface AuthRepository {
    suspend fun devLogin(userId: Long = 1L): Result<Boolean>
    suspend fun guestLogin(): Result<Boolean>
    suspend fun logout(): Result<Unit>
}