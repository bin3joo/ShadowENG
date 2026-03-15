package com.bremenband.shadoweng.feature.content.presentation.range

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.content.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentRangeViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentRangeUiState())
    val uiState: StateFlow<ContentRangeUiState> = _uiState.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<Long>()
    val navigateToStudy: SharedFlow<Long> = _navigateToStudy.asSharedFlow()

    fun init(embedUrl: String) {
        _uiState.update { it.copy(embedUrl = embedUrl) }
    }

    fun onEvent(event: ContentRangeEvent) {
        when (event) {
            is ContentRangeEvent.StartTimeChanged ->
                _uiState.update { it.copy(startTime = event.time, isStartValid = isValidTime(event.time)) }
            is ContentRangeEvent.EndTimeChanged ->
                _uiState.update { it.copy(endTime = event.time, isEndValid = isValidTime(event.time)) }
            is ContentRangeEvent.Submit -> submit()
        }
    }

    private fun submit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // TODO: 백엔드 연동 후 주석 해제
            // val state = _uiState.value
            // repository.createSession(
            //     embedUrl = state.embedUrl,
            //     startSec = parseTimeToSeconds(state.startTime).toDouble(),
            //     endSec = parseTimeToSeconds(state.endTime).toDouble()
            // ).onSuccess { sessionId ->
            //     _navigateToStudy.emit(sessionId)
            // }.onFailure { e ->
            //     _uiState.update { it.copy(error = e.message ?: "세션 생성 실패") }
            // }
            delay(300)
            _navigateToStudy.emit(1L) // TODO: mock - 백엔드 연동 후 실제 sessionId로 교체
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun isValidTime(time: String) =
        Regex("^(?:\\d{1,2}:)?\\d{2}:\\d{2}$").matches(time)

    private fun parseTimeToSeconds(time: String): Int {
        val parts = time.split(":").map { it.toInt() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }
}