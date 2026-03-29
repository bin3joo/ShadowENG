package com.bremenband.shadoweng.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.profile.api.MyInfoApi
import com.bremenband.shadoweng.feature.profile.api.UpdateNicknameRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetNicknameUiState(
    val input: String = "",
    val isLoading: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SetNicknameViewModel @Inject constructor(
    private val myInfoApi: MyInfoApi
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetNicknameUiState())
    val uiState: StateFlow<SetNicknameUiState> = _uiState

    fun onInputChanged(value: String) = _uiState.update { it.copy(input = value) }

    fun confirm() {
        val nickname = _uiState.value.input.trim()
        if (nickname.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { myInfoApi.updateNickname(UpdateNicknameRequest(nickname)) }
                .onSuccess { _uiState.update { it.copy(isDone = true, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}