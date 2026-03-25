package com.bremenband.shadoweng.feature.mypage.presentation.sessions

import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary

data class SessionListState(
    val activeSessions: List<SessionSummary> = emptyList(),
    val completedSessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)