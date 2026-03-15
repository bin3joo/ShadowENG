package com.bremenband.shadoweng.feature.content.repository


import com.bremenband.shadoweng.core.exception.DomainException
import com.bremenband.shadoweng.core.exception.mapDomainException
import com.bremenband.shadoweng.feature.content.api.ContentApi
import com.bremenband.shadoweng.feature.content.api.dto.VideoResponse
import com.bremenband.shadoweng.feature.content.api.dto.CreateSessionRequest
import javax.inject.Inject

class ContentRepositoryImpl @Inject constructor(
    private val api: ContentApi
) : ContentRepository {
    override suspend fun getVideo(url: String): Result<VideoResponse> =
        runCatching { api.getVideo(url).data ?: throw DomainException.InvalidResponse }
            .mapDomainException()

    override suspend fun createSession(embedUrl: String, startSec: Double, endSec: Double): Result<Long> =
        runCatching { api.createSession(CreateSessionRequest(embedUrl, startSec, endSec)).data?.sessionId ?: throw DomainException.InvalidResponse }
            .mapDomainException()
}