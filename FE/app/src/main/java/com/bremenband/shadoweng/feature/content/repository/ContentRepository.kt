package com.bremenband.shadoweng.feature.content.repository

import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.core.network.ApiService
import com.bremenband.shadoweng.core.network.dto.CreateSessionRequest
import com.bremenband.shadoweng.core.network.dto.VideoResponse
import javax.inject.Inject

class ContentRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getVideo(url: String): Result<VideoResponse> =
        runCatching { api.getVideo(url).data ?: throw DomainException.InvalidResponse }
            .mapDomainException()

    suspend fun createSession(embedUrl: String, startSec: Double, endSec: Double): Result<Long> =
        runCatching { api.createSession(CreateSessionRequest(embedUrl, startSec, endSec)).data?.sessionId ?: throw DomainException.InvalidResponse }
            .mapDomainException()
}