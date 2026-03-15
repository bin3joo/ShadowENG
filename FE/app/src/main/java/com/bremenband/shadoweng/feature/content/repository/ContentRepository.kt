package com.bremenband.shadoweng.feature.content.repository

import com.bremenband.shadoweng.feature.content.api.dto.VideoResponse

interface ContentRepository {
    suspend fun getVideo(url: String): Result<VideoResponse>
    suspend fun createSession(embedUrl: String, startSec: Double, endSec: Double): Result<Long>
}