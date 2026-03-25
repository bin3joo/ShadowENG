package com.bremenband.shadoweng.feature.home.repository

import com.bremenband.shadoweng.feature.home.domain.model.UserProfile  // 추가
import com.bremenband.shadoweng.feature.home.presentation.LatestSession

interface HomeRepository {
    suspend fun getRecentSession(): Result<LatestSession?>
    suspend fun getUserMe(): Result<UserProfile>
}