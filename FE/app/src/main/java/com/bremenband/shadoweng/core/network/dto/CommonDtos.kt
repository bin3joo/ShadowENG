package com.bremenband.shadoweng.core.network.dto

data class ApiResponse<T>(
    val isSuccess: Boolean,
    val data: T?,
    val message: String?,
    val code: Int
)