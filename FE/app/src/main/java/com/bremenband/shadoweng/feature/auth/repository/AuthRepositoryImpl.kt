package com.bremenband.shadoweng.feature.auth.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.auth.api.AuthApi
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

// TODO: 게스트 로그인 활성화 시 주석 해제
// override suspend fun guestLogin(): Result<Unit> = runCatching {
//     val token = api.guestLogin().data?.token ?: throw DomainException.NotFound
//     tokenStorage.saveToken(token)
// }.mapDomainException()

    override suspend fun logout(): Result<Unit> = runCatching {
        api.logout()
        tokenStorage.clear()
    }
}