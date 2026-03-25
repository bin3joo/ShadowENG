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

    private val _navigateToSessionEnd = MutableSharedFlow<Long>() // reportId (-1 = 실패)
    val navigateToSessionEnd: SharedFlow<Long> = _navigateToSessionEnd.asSharedFlow()

    private val _navigateToNextSentence = MutableSharedFlow<Long>() // nextSentenceId
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
                            thumbnailUrl = session.thumbnailUrl ?: ""
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
                    else -> return
                }
                _uiState.update { it.copy(expressionInfo = buildMockExpressionInfo(event.word, status)) }
            }

            is StudyHighlightEvent.DismissExpression ->
                _uiState.update { it.copy(selectedWord = null, expressionInfo = null) }

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
                            // 마지막 문장이고 모두 완료 → 리포트 생성
                            studyRepository.createReport(currentSessionId)
                                .onSuccess { report -> _navigateToSessionEnd.emit(report.reportId) }
                                .onFailure { _navigateToSessionEnd.emit(-1L) }
                        } else {
                            // 마지막 문장이지만 미완료 문장 있음 → 세션으로 돌아가기
                            _navigateToNextSentence.emit(-1L)
                        }
                    } else {
                        studyRepository.createReport(currentSessionId)
                            .onSuccess { report -> _navigateToSessionEnd.emit(report.reportId) }
                            .onFailure { _navigateToSessionEnd.emit(-1L) }
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

    private fun buildMockExpressionInfo(word: String, status: String): ExpressionInfo {
        return when (status) {
            "missed" -> ExpressionInfo(word = word, pronunciation = "",
                description = "이 단어가 발화에서 감지되지 않았어요. 문장 흐름 속에서 자연스럽게 발음해보세요.",
                examples = listOf("천천히 따라 말하며 위치를 확인해보세요.", "앞뒤 단어와 연음이 되는 경우가 많아요."))
            "dragged" -> ExpressionInfo(word = word, pronunciation = "",
                description = "이 단어를 필요 이상으로 길게 발음했어요. 좀 더 짧고 가볍게 처리해보세요.",
                examples = listOf("영어는 강세 있는 음절 외에는 짧게 처리해요.", "기능어(관사, 전치사 등)는 특히 약하게 발음해요."))
            "rushed" -> ExpressionInfo(word = word, pronunciation = "",
                description = "이 단어를 너무 빠르게 발음했어요. 조금 더 여유를 두고 말해보세요.",
                examples = listOf("내용어(명사, 동사)는 충분히 강조해서 말해요.", "의미 전달에 중요한 단어는 늘려서 발음하는 게 자연스러워요."))
            else -> ExpressionInfo(word = word, pronunciation = "",
                description = "잘 발음했어요! 이 단어는 문제없이 인식됐어요.", examples = emptyList())
        }
    }
}