package com.bremenband.shadoweng.feature.study.presentation.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.core.ui.component.AudioRecordButton
import com.bremenband.shadoweng.core.ui.component.ExitConfirmDialog
import com.bremenband.shadoweng.core.ui.component.YoutubePlayerView
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import androidx.compose.animation.*
import com.bremenband.shadoweng.core.ui.component.TooShortRecordingModal
import com.bremenband.shadoweng.core.ui.component.VoiceNotRecognizedModal
import com.bremenband.shadoweng.feature.study.presentation.component.StepIndicator

@Composable
fun StudyLearningScreen(
    sessionId: Long,
    sentenceId: Long,
    step: Int,
    onNavigateToHighlight: (completedStep: Int) -> Unit,
    onSessionEnd: () -> Unit,
    onExit: () -> Unit,
    viewModel: StudyLearningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToHighlight.collect { onNavigateToHighlight(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToReport.collect { onSessionEnd() }
    }
    LaunchedEffect(sentenceId) {
        viewModel.init(sessionId, SentenceItem(id = sentenceId, timestamp = "", content = ""), step)
    }

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        ExitConfirmDialog(
            message = "지금 학습을 중단하면 기록이 저장되지 않아요.\n정말 중단하시겠어요?",
            onConfirm = { onExit() },
            onDismiss = { showExitDialog = false }
        )
    }

    if (uiState.showVoiceNotRecognizedModal) {
        VoiceNotRecognizedModal(
            onRetry = {
                viewModel.onEvent(StudyLearningEvent.DismissVoiceModal)
                viewModel.onEvent(StudyLearningEvent.RetryRecording)
            }
        )
    }

    if (uiState.showTooShortModal) {
        TooShortRecordingModal(
            onRetry = { viewModel.onEvent(StudyLearningEvent.DismissTooShortModal) }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F0))) {
        if (uiState.showVoiceNotRecognizedModal) {
            VoiceNotRecognizedModal(
                onRetry = {
                    viewModel.onEvent(StudyLearningEvent.DismissVoiceModal)
                    viewModel.onEvent(StudyLearningEvent.RetryRecording)
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            YoutubePlayerView(
                embedUrl = uiState.embedUrl,
                startSec = uiState.startSec,
                endSec = uiState.endSec,
                autoPlay = step == 1,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )

            StepIndicator(
                currentStep = uiState.subtitleMode.ordinal + 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    when (uiState.subtitleMode) {
                        SubtitleMode.NONE -> NoneSubtitleCardContent(isFinal = false)
                        SubtitleMode.FULL -> uiState.sentence?.let { FullSubtitleCardContent(it) }
                        SubtitleMode.PARTIAL -> uiState.sentence?.let { PartialSubtitleCardContent(it) }
                        SubtitleMode.NONE_FINAL -> NoneSubtitleCardContent(isFinal = true)
                    }

                    AudioRecordButton(
                        isRecording = uiState.isRecording,
                        onStartCountdown = { viewModel.onEvent(StudyLearningEvent.StartRecording) },
                        onStopRecording = { viewModel.onEvent(StudyLearningEvent.StopRecording) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.showEncourageModal,
            enter = fadeIn(tween(300)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("🎉", fontSize = 40.sp)
                        Text(
                            "좋아요! 첫 번째 도전 완료!",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF362000)
                        )
                        Text(
                            "이제 자막과 함께\n따라 말해봐요",
                            fontSize = 14.sp, color = Color(0xFF888888),
                            textAlign = TextAlign.Center, lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isAnalyzing || uiState.isNavigating,
            enter = fadeIn(tween(300)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFEDF57),
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 5.dp
                        )
                        AnimatedContent(
                            targetState = uiState.isAnalyzing,
                            transitionSpec = {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                            },
                            label = "loadingText"
                        ) { isAnalyzing ->
                            Text(
                                if (isAnalyzing) "발음 분석 중..." else "다음 단계로 이동 중...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF362000)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.toPartialSubtitle(): String {
    val words = split(" ")
    return words.mapIndexed { i, word ->
        if (i % 3 == 2 && word.length > 3) "_".repeat(word.length) else word
    }.joinToString(" ")
}

@Composable
private fun NoneSubtitleCardContent(isFinal: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isFinal) {
            Text(
                "자막 없이 따라 말해보세요!\n이번에는 평가가 진행돼요.",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, color = Color(0xFF1A1A1A),
                lineHeight = 24.sp
            )
        } else {
            Text(
                "자막 없이 먼저 말해보고,\n점차 자막 없이 말할 수 있도록 연습해요!",
                fontSize = 14.sp, textAlign = TextAlign.Center,
                color = Color(0xFF444444), lineHeight = 22.sp
            )
            Text(
                "첫 단계는 가볍게 시작하는 구간이에요.\n평가 없이 편하게 말해보세요!",
                fontSize = 16.sp, textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF444444), lineHeight = 22.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("버튼을 눌러 녹음을 시작하세요!", fontSize = 13.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun FullSubtitleCardContent(sentence: SentenceItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "자막과 함께 따라 말해보세요!\n이번에는 발음을 분석합니다!",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            color = Color(0xFF444444), lineHeight = 22.sp
        )
        Text(
            sentence.content,
            fontSize = 18.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start, color = Color(0xFF1A1A1A),
            lineHeight = 32.sp, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PartialSubtitleCardContent(sentence: SentenceItem) {
    val partialText = sentence.content.toPartialSubtitle()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "자막을 조금 지우고 따라 말해 볼까요?",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            color = Color(0xFF444444), lineHeight = 22.sp
        )
        Text(
            partialText,
            fontSize = 18.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start, color = Color(0xFF1A1A1A),
            lineHeight = 24.sp, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("버튼을 눌러 녹음을 시작하세요!", fontSize = 13.sp, color = Color(0xFF888888))
    }
}