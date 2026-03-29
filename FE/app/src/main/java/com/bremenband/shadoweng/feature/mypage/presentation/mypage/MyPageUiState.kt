package com.bremenband.shadoweng.feature.mypage.presentation.mypage

import com.bremenband.shadoweng.feature.home.presentation.LatestSession

data class MyPageUiState(
    val nickname: String = "",
    val email: String = "",
    val isLoading: Boolean = true,
    val latestSession: LatestSession? = null,
    val leagueTopScore: Int = 0,
    val myScore: Int = 0,
    val tier: String = ""
)

sealed class MyPageEvent {
    object ClickRegister : MyPageEvent()
    object ClickLatestSession : MyPageEvent()
    object ClickGame : MyPageEvent()
}