package com.bremenband.shadoweng.feature.home.mapper

import com.bremenband.shadoweng.feature.home.presentation.LatestSession
import com.bremenband.shadoweng.feature.study.api.dto.LatestActiveSessionDto

fun LatestActiveSessionDto.toLatestSession(): LatestSession = LatestSession(
    sessionId = sessionId,
    thumbnailUrl = thumbnails?.standard?.url ?: "",
    progressRate = progressRate,
    title = "",           // TODO: 백엔드 필드 추가 후 연결
    completedCount = 0,   // TODO
    totalCount = 0        // TODO
)