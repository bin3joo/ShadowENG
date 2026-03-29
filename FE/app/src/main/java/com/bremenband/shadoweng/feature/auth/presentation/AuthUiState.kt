package com.bremenband.shadoweng.feature.auth.presentation

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val isNew: Boolean) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}