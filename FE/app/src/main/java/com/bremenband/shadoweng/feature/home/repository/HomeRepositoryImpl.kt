package com.bremenband.shadoweng.feature.home.repository

import com.bremenband.shadoweng.feature.home.api.HomeApi              // 추가
import com.bremenband.shadoweng.feature.home.domain.model.UserProfile  // 추가
import com.bremenband.shadoweng.feature.home.mapper.toLatestSession
import com.bremenband.shadoweng.feature.home.mapper.toUserProfile       // 추가
import com.bremenband.shadoweng.feature.home.presentation.LatestSession
import com.bremenband.shadoweng.feature.study.api.StudyApi
import javax.inject.Inject

class HomeRepositoryImpl @Inject constructor(
    private val studyApi: StudyApi,
    private val homeApi: HomeApi
) : HomeRepository {

    override suspend fun getRecentSession(): Result<LatestSession?> = runCatching {
        studyApi.getRecentSession().data?.latestActiveSession?.toLatestSession()
    }

    override suspend fun getUserMe(): Result<UserProfile> = runCatching {
        homeApi.getUserMe().data?.toUserProfile()
            ?: error("유저 정보 없음")
    }
}