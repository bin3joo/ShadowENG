package com.bremenband.shadoweng.feature.content.api.dto

data class VideoResponse(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int,
    val channelTitle: String,
    val embedUrl: String
)
data class CreateSessionRequest(
    val embedUrl: String,
    val startSec: Double,
    val endSec: Double)