package com.bremenband.shadoweng.feature.study.presentation.highlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremenband.shadoweng.core.ui.component.model.AnnotationType
import com.bremenband.shadoweng.core.ui.component.model.ExpressionInfo
import com.bremenband.shadoweng.feature.study.domain.StudySession
import com.bremenband.shadoweng.feature.study.mapper.toAnnotations
import com.bremenband.shadoweng.feature.study.repository.StudyRepository
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
class StudyHighlightViewModel @Inject constructor(
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyHighlightUiState())
    val uiState: StateFlow<StudyHighlightUiState> = _uiState.asStateFlow()

    private val _navigateToNextMode = MutableSharedFlow<Unit>()
    val navigateToNextMode: SharedFlow<Unit> = _navigateToNextMode.asSharedFlow()

    private val _navigateToSessionEnd = MutableSharedFlow<Long>()
    val navigateToSessionEnd: SharedFlow<Long> = _navigateToSessionEnd.asSharedFlow()

    private val _navigateToNextSentence = MutableSharedFlow<Long>()
    val navigateToNextSentence: SharedFlow<Long> = _navigateToNextSentence.asSharedFlow()

    private val _navigateRetry = MutableSharedFlow<Unit>()
    val navigateRetry: SharedFlow<Unit> = _navigateRetry.asSharedFlow()

    private var currentSessionId: Long = 0L
    private var cachedSession: StudySession? = null

    fun init(sessionId: Long, sentenceId: Long, step: Int) {
        currentSessionId = sessionId
        viewModelScope.launch {
            studyRepository.getSession(sessionId)
                .onSuccess { session ->
                    cachedSession = session
                    val originalSentence = session.sentences.find { it.id == sentenceId }
                    val evaluation = studyRepository.getCachedEvaluation()
                    val content = originalSentence?.content ?: ""
                    val annotations = evaluation?.toAnnotations(content) ?: emptyList()
                    val currentIndex = session.sentences.indexOfFirst { it.id == sentenceId }
                    val isLastSentence = currentIndex == session.sentences.lastIndex

                    _uiState.update {
                        it.copy(
                            sessionId = sessionId,
                            sentence = content,
                            sentenceId = sentenceId,
                            startSec = originalSentence?.startSec ?: 0.0,
                            endSec = originalSentence?.endSec ?: 0.0,
                            annotations = annotations,
                            evaluationResult = evaluation,
                            embedUrl = session.watchUrl,
                            thumbnailUrl = session.thumbnailUrl ?: "",
                            isLastSentence = isLastSentence,
                            sentenceKo = originalSentence?.sentenceKo ?: "",
                            vocabulary = originalSentence?.vocabulary ?: emptyList()
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onEvent(event: StudyHighlightEvent) {
        when (event) {
            is StudyHighlightEvent.ToggleKoreanSubtitle ->
                _uiState.update { it.copy(showKoreanSubtitle = !it.showKoreanSubtitle) }

            is StudyHighlightEvent.TapWord -> {
                val state = _uiState.value
                val tappedAnnotation = state.annotations.firstOrNull { ann ->
                    event.charIndex in ann.startIndex until ann.endIndex
                }
                if (tappedAnnotation == null) return

                val status = when {
                    tappedAnnotation.type == AnnotationType.HIGHLIGHT -> "missed"
                    tappedAnnotation.color == 0xFFFFB800L -> "dragged"
                    tappedAnnotation.color == 0xFF1565C0L -> "rushed"
                    tappedAnnotation.type == AnnotationType.ARROW_UP -> "arrow_up"
                    tappedAnnotation.type == AnnotationType.ARROW_DOWN -> "arrow_down"
                    else -> return
                }

                val wordFeedback = state.evaluationResult?.wordFeedback
                    ?.find { it.word.trim('.', ',', '!', '?', '"') == event.word }

                val expressionList = listOf(buildWordFeedbackInfo(event.word, wordFeedback?.status ?: status))
                _uiState.update { it.copy(expressionInfoList = expressionList) }
            }

            is StudyHighlightEvent.DismissExpression ->
                _uiState.update { it.copy(selectedWord = null, expressionInfoList = emptyList()) }

            is StudyHighlightEvent.RetryRecording ->
                viewModelScope.launch { _navigateRetry.emit(Unit) }

            is StudyHighlightEvent.NextMode ->
                viewModelScope.launch { _navigateToNextMode.emit(Unit) }

            is StudyHighlightEvent.SessionEnd -> {
                viewModelScope.launch {
                    val session = cachedSession
                    val currentSentenceId = _uiState.value.sentenceId

                    if (session != null) {
                        val currentIndex = session.sentences.indexOfFirst { it.id == currentSentenceId }
                        val nextSentence = session.sentences.getOrNull(currentIndex + 1)
                        val allCompleted = session.sentences.all { it.isCompleted } ||
                                (nextSentence == null && session.sentences
                                    .filterIndexed { i, _ -> i != currentIndex }
                                    .all { it.isCompleted })

                        if (nextSentence != null) {
                            _navigateToNextSentence.emit(nextSentence.id)
                        } else if (allCompleted) {
                            studyRepository.getReports(currentSessionId)
                                .onSuccess { reports ->
                                    val latestReportId = reports.maxByOrNull { it.reportId }?.reportId
                                    if (latestReportId != null) {
                                        _navigateToSessionEnd.emit(latestReportId)
                                    } else {
                                        _navigateToSessionEnd.emit(-1L)
                                    }
                                }
                                .onFailure { _navigateToSessionEnd.emit(-1L) }
                        } else {
                            _navigateToNextSentence.emit(-1L)
                        }
                    } else {
                        _navigateToSessionEnd.emit(-1L)
                    }
                }
            }

            is StudyHighlightEvent.ToggleBookmark -> {
                val sentenceId = _uiState.value.sentenceId
                val newState = !_uiState.value.isBookmarked
                viewModelScope.launch {
                    studyRepository.toggleBookmark(sentenceId, newState)
                        .onSuccess { _uiState.update { it.copy(isBookmarked = newState) } }
                        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                }
            }
        }
    }

    private fun buildWordFeedbackInfo(word: String, status: String): ExpressionInfo {
        return when (status) {
            "missed" -> ExpressionInfo(
                word = word,
                pronunciation = "",
                description = "이 단어가 발화에서 감지되지 않았어요. 문장 흐름 속에서 자연스럽게 발음해보세요.",
                examples = listOf("천천히 따라 말하며 위치를 확인해보세요.", "앞뒤 단어와 연음이 되는 경우가 많아요.")
            )
            "dragged" -> ExpressionInfo(
                word = word,
                pronunciation = "",
                description = "이 단어를 필요 이상으로 길게 발음했어요. 좀 더 짧고 가볍게 처리해보세요.",
                examples = listOf("영어는 강세 있는 음절 외에는 짧게 처리해요.", "기능어(관사, 전치사 등)는 특히 약하게 발음해요.")
            )
            "rushed" -> ExpressionInfo(
                word = word,
                pronunciation = "",
                description = "이 단어를 너무 짧게 발음했어요. 조금 더 여유를 두고 말해보세요.",
                examples = listOf("내용어(명사, 동사)는 충분히 강조해서 말해요.", "의미 전달에 중요한 단어는 늘려서 발음하는 게 자연스러워요.")
            )
            "arrow_up" -> ExpressionInfo(
                word = word, pronunciation = "",
                description = "문장 끝 억양이 반대 방향이에요. 올라가는 억양으로 마무리해보세요.",
                examples = listOf("의문문은 끝을 올려서 말해요.", "확인을 구하는 표현도 억양을 올리는 게 자연스러워요.")
            )
            "arrow_down" -> ExpressionInfo(
                word = word, pronunciation = "",
                description = "문장 끝 억양이 너무 약해요. 좀 더 명확하게 내려주세요.",
                examples = listOf("평서문은 끝을 내리면서 마무리해요.", "단호한 표현일수록 억양을 확실히 내려주세요.")
            )
            else -> ExpressionInfo(
                word = word,
                pronunciation = "",
                description = "잘 발음했어요! 이 단어는 문제없이 인식됐어요.",
                examples = emptyList()
            )
        }
    }
}