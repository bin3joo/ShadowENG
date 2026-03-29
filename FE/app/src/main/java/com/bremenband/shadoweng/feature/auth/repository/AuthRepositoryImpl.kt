package com.bremenband.shadoweng.feature.auth.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.auth.api.AuthApi
import com.bremenband.shadoweng.feature.auth.api.dto.GuestLoginRequest
import com.bremenband.shadoweng.feature.auth.data.TokenStorage
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {
    override suspend fun guestLogin(): Result<Boolean> = runCatching {
        val deviceId = tokenStorage.getOrCreateDeviceId()
        val response = api.guestLogin(GuestLoginRequest(deviceId)).data
            ?: throw DomainException.NotFound
        tokenStorage.saveToken(response.accessToken)
        tokenStorage.saveRefreshToken(response.refreshToken)
        response.isNew
    }.mapDomainException()

    override suspend fun devLogin(userId: Long): Result<Boolean> = runCatching {
        val response = api.devLogin(1L).data ?: throw DomainException.NotFound
        tokenStorage.saveToken(response.accessToken)
        tokenStorage.saveRefreshToken(response.refreshToken)
        response.isNew
    }.mapDomainException()

    override suspend fun logout(): Result<Unit> = runCatching {
        api.logout()
        tokenStorage.clear()
    }.mapDomainException()
}