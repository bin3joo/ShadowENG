package com.bremenband.shadoweng.feature.home.repository

import com.bremenband.shadoweng.feature.home.api.dto.DashboardResponse
import com.bremenband.shadoweng.feature.home.domain.model.UserProfile
import com.bremenband.shadoweng.feature.home.presentation.LatestSession


interface HomeRepository {
    suspend fun getRecentSession(): Result<LatestSession?>
    suspend fun getUserMe(): Result<UserProfile>
    suspend fun getDashboard(): Result<DashboardResponse>
}