package com.bremenband.shadoweng.feature.home.repository

import com.bremenband.shadoweng.feature.home.presentation.LatestSession

interface HomeRepository {
    suspend fun getRecentSession(): Result<LatestSession?>
}