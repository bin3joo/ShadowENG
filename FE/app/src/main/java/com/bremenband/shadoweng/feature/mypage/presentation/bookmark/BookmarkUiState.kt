package com.bremenband.shadoweng.feature.mypage.presentation.bookmark

import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary

data class BookmarkUiState(
    val isLoading: Boolean = true,
    val bookmarkedSessions: List<SessionSummary> = emptyList(),
    val error: String? = null
)