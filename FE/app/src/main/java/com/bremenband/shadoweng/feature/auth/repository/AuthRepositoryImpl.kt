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
    override suspend fun devLogin(userId: Long): Result<Unit> = runCatching {
        val response = api.devLogin(1L).data ?: throw DomainException.NotFound
        tokenStorage.saveToken(response.accessToken)
        tokenStorage.saveRefreshToken(response.refreshToken)
        // TODO: refreshToken도 저장 필요 시 tokenStorage.saveRefreshToken(response.refreshToken)
    }.mapDomainException()

    override suspend fun guestLogin(): Result<Unit> = runCatching {
        val deviceId = tokenStorage.getOrCreateDeviceId()
        val response = api.guestLogin(GuestLoginRequest(deviceId)).data
            ?: throw DomainException.NotFound
        tokenStorage.saveToken(response.accessToken)
        tokenStorage.saveRefreshToken(response.refreshToken)
    }.mapDomainException()

    override suspend fun logout(): Result<Unit> = runCatching {
        api.logout()
        tokenStorage.clear()
    }.mapDomainException()
}