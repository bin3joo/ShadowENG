package com.bremenband.shadoweng.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.auth.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository  // Context 제거
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun guestLogin() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.guestLogin()
                .onSuccess { _uiState.value = AuthUiState.Success }
                .onFailure { e -> _uiState.value = AuthUiState.Error(e.message ?: "네트워크 오류") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
                .onSuccess { _uiState.value = AuthUiState.Idle }
                .onFailure { e -> _uiState.value = AuthUiState.Error(e.message ?: "로그아웃 실패") }
        }
    }
}