package com.bremenband.shadoweng.feature.content.presentation.range

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.feature.content.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _navigateToLoading = MutableSharedFlow<Triple<String, Double, Double>>()
    val navigateToLoading: SharedFlow<Triple<String, Double, Double>> = _navigateToLoading.asSharedFlow()

    fun init(embedUrl: String) {
        _uiState.update { it.copy(embedUrl = embedUrl) }
        viewModelScope.launch {
            val youtubeUrl = embedUrl.replace("https://www.youtube.com/embed/", "https://www.youtube.com/watch?v=")
            repository.getVideo(youtubeUrl)
                .onSuccess { video ->
                    val duration = video.duration.toFloat()
                    val defaultEnd = minOf(120f, duration)
                    _uiState.update {
                        it.copy(
                            thumbnailUrl = video.thumbnailUrl ?: "",
                            videoDuration = duration,
                            sliderEnd = defaultEnd,
                            endTime = formatSeconds(defaultEnd.toInt())
                        )
                    }
                }
        }
    }

    fun onEvent(event: ContentRangeEvent) {
        when (event) {
            is ContentRangeEvent.StartTimeChanged ->
                _uiState.update { it.copy(startTime = event.time, isStartValid = isValidTime(event.time)) }
            is ContentRangeEvent.EndTimeChanged ->
                _uiState.update { it.copy(endTime = event.time, isEndValid = isValidTime(event.time)) }
            is ContentRangeEvent.Submit -> submit()
            is ContentRangeEvent.SliderStartChanged -> {
                val seconds = event.seconds.toInt()
                _uiState.update {
                    it.copy(
                        sliderStart = event.seconds,
                        startTime = formatSeconds(seconds),
                        isStartValid = true
                    )
                }
            }
            is ContentRangeEvent.SliderEndChanged -> {
                val seconds = event.seconds.toInt()
                _uiState.update {
                    it.copy(
                        sliderEnd = event.seconds,
                        endTime = formatSeconds(seconds),
                        isEndValid = true
                    )
                }
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.isStartValid || !state.isEndValid) return
        if (state.sliderEnd - state.sliderStart < 1f) return
        viewModelScope.launch {
            _navigateToLoading.emit(
                Triple(
                    state.embedUrl,
                    parseTimeToSeconds(state.startTime).toDouble(),
                    parseTimeToSeconds(state.endTime).toDouble()
                )
            )
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

    private fun formatSeconds(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}