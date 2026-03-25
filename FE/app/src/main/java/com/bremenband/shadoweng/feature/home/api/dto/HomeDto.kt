package com.bremenband.shadoweng.feature.home.api.dto

data class UserMeResponse(
    val code: Int,
    val message: String,
    val data: UserMeDto?,
    val isSuccess: Boolean
)

data class UserMeDto(
    val userId: Long,
    val nickname: String,
    val email: String,
    val totalVisitedDays: Int,
    val totalStudyDays: Int,
    val longestStreak: Int,
    val studyDates: List<String>,  // "YYYY-MM-DD"
    val createdAt: String
)

// TODO: 출석 API 스펙 확정 후 StreakDto 추가