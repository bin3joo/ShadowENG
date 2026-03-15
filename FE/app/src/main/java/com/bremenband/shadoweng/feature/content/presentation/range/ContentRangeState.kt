package com.bremenband.shadoweng.feature.content.presentation.range

data class ContentRangeUiState(
    val embedUrl: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val isStartValid: Boolean = false,
    val isEndValid: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ContentRangeEvent {
    data class StartTimeChanged(val time: String) : ContentRangeEvent()
    data class EndTimeChanged(val time: String) : ContentRangeEvent()
    object Submit : ContentRangeEvent()
}