package com.bremenband.shadoweng.feature.auth.repository

interface AuthRepository {
    suspend fun guestLogin(): Result<Unit>
    suspend fun logout(): Result<Unit>
}