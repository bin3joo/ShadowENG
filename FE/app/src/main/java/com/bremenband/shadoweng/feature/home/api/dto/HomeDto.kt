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
    val email: String?,
    val totalVisitedDays: Int,
    val totalStudyDays: Int,
    val longestStreak: Int,
    val studyDates: List<String>,  // "YYYY-MM-DD"
    val createdAt: String
)

data class DashboardResponse(
    val longestStreak: Int,
    val totalAttendanceDays: Int,
    val totalStudiedSentences: Int,
    val totalStudyTimeSeconds: Double,
    val studyDates: List<String>,
    val reportDates: List<ReportDateDto>
)

data class ReportDateDto(
    val date: String,
    val reports: List<ReportItemDto>
)

data class ReportItemDto(
    val reportId: Long,
    val sessionId: Long,
    val thumbnailUrl: String?,
    val videoTitle: String?,
    val totalScore: Double?
)

// TODO: 출석 API 스펙 확정 후 StreakDto 추가