package com.bremenband.shadoweng.feature.content.presentation.range

data class ContentRangeUiState(
    val embedUrl: String = "",
    val thumbnailUrl: String = "",
    val startTime: String = "00:00",
    val endTime: String = "02:00",
    val isStartValid: Boolean = true,
    val isEndValid: Boolean = true,
    val videoDuration: Float = 600f // TODO: API에서 받아오기
)

sealed class ContentRangeEvent {
    data class StartTimeChanged(val time: String) : ContentRangeEvent()
    data class EndTimeChanged(val time: String) : ContentRangeEvent()
}

