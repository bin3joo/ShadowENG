package com.bremenband.shadoweng.core.exception

import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException

fun <T> Result<T>.mapDomainException(): Result<T> =
    recoverCatching { e ->
        throw when (e) {
            is DomainException -> e
            is CancellationException -> e
            is HttpException -> if (e.code() == 404) DomainException.NotFound
            else DomainException.Unknown(e.message())
            is IOException -> DomainException.NetworkError
            else -> DomainException.Unknown(e.message ?: "알 수 없는 오류가 발생했어요")
        }
    }