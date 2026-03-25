package com.bremenband.shadoweng.feature.mypage.domain.model

data class MyPageProfile(
    val userId: Long,
    val nickname: String,
    val email: String,
    val visitedCount: Int
)