package com.bremenband.shadoweng.core.exception

import okio.IOException
import retrofit2.HttpException

fun <T> Result<T>.mapDomainException(): Result<T> =
    recoverCatching { e ->
        throw when (e) {
            is DomainException -> e
            is HttpException -> if (e.code() == 404) DomainException.NotFound
            else DomainException.Unknown(e.message())
            is IOException -> DomainException.NetworkError
            else -> DomainException.Unknown(e.message)
        }
    }