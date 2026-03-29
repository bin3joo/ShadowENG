package com.bremenband.shadoweng.feature.profile.presentation

data class MyInfoUiState(
    val nickname: String = "",
    val email: String = "",
    val tier: String = "",
    val totalVisitedDays: Int = 0,
    val longestStreak: Int = 0,
    val bookmarkCount: Int = 0,
    val weeklyScore: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showNicknameDialog: Boolean = false,
    val nicknameInput: String = "",
    val isNicknameUpdating: Boolean = false
)