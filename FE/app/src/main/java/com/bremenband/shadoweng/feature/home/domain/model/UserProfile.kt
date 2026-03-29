package com.bremenband.shadoweng.feature.home.domain.model

import java.time.LocalDate

data class UserProfile(
    val userId: Long,
    val nickname: String,
    val email: String?,
    val totalVisitedDays: Int,
    val totalStudyDays: Int,
    val longestStreak: Int,
    val studyDates: Set<LocalDate>,
    val createdAt: String
)

// TODO: 출석 API 스펙 확정 후 StreakInfo 추가
// data class StreakInfo(
//     val longestStreak: Int,
//     val totalAttendance: Int,
//     val attendedDates: List<String>  // "2026-03-09" 형식
// )