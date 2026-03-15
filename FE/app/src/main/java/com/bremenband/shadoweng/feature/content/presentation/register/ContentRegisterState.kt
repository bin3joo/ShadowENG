package com.bremenband.shadoweng.feature.content.presentation.register

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