package com.bremenband.shadoweng.core.exception

sealed class DomainException : Exception() {
    object NotFound : DomainException()
    object NetworkError : DomainException()
    object InvalidResponse : DomainException()
    data class Unknown(override val message: String = "알 수 없는 오류가 발생했어요") : DomainException()
    data class ApiError(val code: Int, override val message: String) : DomainException()
}