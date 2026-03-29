package com.bremenband.shadoweng.core.network.dto

import com.bremenband.shadoweng.core.exception.DomainException

data class ApiResponse<T>(
    val isSuccess: Boolean,
    val data: T?,
    val message: String?,
    val code: Int
) {
    fun getOrThrow(): T? {
        if (!isSuccess) {
            throw DomainException.ApiError(code, message ?: "오류가 발생했어요")
        }
        return data
    }
}