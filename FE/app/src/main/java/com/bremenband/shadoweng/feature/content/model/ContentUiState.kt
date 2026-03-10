package com.bremenband.shadoweng.feature.content.model

data class ContentRegisterUiState(
    val url: String = "",
    val videoId: String? = null,
    val isValidUrl: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ContentRegisterEvent {
    data class UrlChanged(val url: String) : ContentRegisterEvent()
    object Submit : ContentRegisterEvent()
}

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