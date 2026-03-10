package com.bremenband.shadoweng.core.exception

sealed class DomainException : Exception() {
    object NotFound : DomainException()
    object NetworkError : DomainException()
    data class Unknown(override val message: String?) : DomainException()
}