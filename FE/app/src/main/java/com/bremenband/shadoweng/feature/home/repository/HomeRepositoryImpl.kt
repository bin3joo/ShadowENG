package com.bremenband.shadoweng.feature.home.repository

import com.bremenband.shadoweng.feature.home.mapper.toLatestSession
import com.bremenband.shadoweng.feature.home.presentation.LatestSession
import com.bremenband.shadoweng.feature.study.api.StudyApi
import javax.inject.Inject

class HomeRepositoryImpl @Inject constructor(
    private val studyApi: StudyApi
) : HomeRepository {
    override suspend fun getRecentSession(): Result<LatestSession?> = runCatching {
        studyApi.getRecentSession().data?.latestActiveSession?.toLatestSession()
    }
}