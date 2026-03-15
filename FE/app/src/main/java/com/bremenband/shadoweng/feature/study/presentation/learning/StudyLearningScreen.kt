package com.bremenband.shadoweng.feature.study.presentation.learning

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.AudioRecordButton
import com.bremenband.shadoweng.feature.study.domain.SentenceItem
import kotlinx.coroutines.launch

@Composable
fun StudyLearningScreen(
    sessionId: Long,
    sentenceId: Long,
    onNavigateToHighlight: (sentenceId: Long) -> Unit,
    onSessionEnd: () -> Unit,
    viewModel: StudyLearningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.showAutoAdvanceSnackbar.collect {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "3초 후 다음 단계로 넘어가요",
                    actionLabel = "취소",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.cancelAutoAdvance()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToHighlight.collect { onNavigateToHighlight(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToReport.collect { onSessionEnd() }
    }

    LaunchedEffect(sentenceId) {
        viewModel.init(sessionId, SentenceItem(id = sentenceId, timestamp = "0:03", content = ""))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF5F5F0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )

            StepIndicator(
                currentStep = uiState.subtitleMode.ordinal,
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
                        SubtitleMode.PARTIAL -> uiState.sentence?.let {
                            PartialSubtitleCardContent(
                                it
                            )
                        }

                        SubtitleMode.NONE_FINAL -> NoneSubtitleCardContent(isFinal = true)
                    }

                    AudioRecordButton(
                        isRecording = uiState.isRecording,
                        countdown = uiState.countdown,
                        onStartCountdown = { viewModel.onEvent(StudyLearningEvent.StartCountdown) },
                        onStopRecording = { viewModel.onEvent(StudyLearningEvent.StopRecording) }
                    )
                }
            }
        }
    }
}

// TODO: 백엔드 partialContent 필드 추가 후 제거
private fun String.toPartialSubtitle(): String {
    val words = split(" ")
    return words.mapIndexed { i, word ->
        if (i % 3 == 2 && word.length > 3) "_".repeat(word.length) else word
    }.joinToString(" ")
}

@Composable
private fun NoneSubtitleCardContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "학습은 총 4단계로 진행돼요.",
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, color = Color(0xFF1A1A1A)
        )
        Text(
            "자막 없이 먼저 말해보고,\n점차 자막 없이 말할 수 있도록 연습해요!",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            color = Color(0xFF444444), lineHeight = 22.sp
        )
        Text(
            "첫 단계는 가볍게 시작하는 구간이에요.\n평가 없이 편하게 말해보세요!",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF444444), lineHeight = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "버튼을 눌러 녹음을 시작하세요!",
            fontSize = 13.sp, color = Color(0xFF888888)
        )
    }
}

@Composable
private fun FullSubtitleCardContent(sentence: SentenceItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("자막과 함께 따라 말해보세요!\n이번에는 발음을 분석합니다!", fontSize = 14.sp, textAlign = TextAlign.Center, color = Color(0xFF444444), lineHeight = 22.sp)
        Text(sentence.content, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, color = Color(0xFF1A1A1A), lineHeight = 32.sp, modifier = Modifier.fillMaxWidth())
    }
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
                "학습은 총 4단계로 진행돼요.",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, color = Color(0xFF1A1A1A)
            )
            Text(
                "자막 없이 먼저 말해보고,\n점차 자막 없이 말할 수 있도록 연습해요!",
                fontSize = 14.sp, textAlign = TextAlign.Center,
                color = Color(0xFF444444), lineHeight = 22.sp
            )
            Text(
                "첫 단계는 가볍게 시작하는 구간이에요.\n평가 없이 편하게 말해보세요!",
                fontSize = 14.sp, textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF444444), lineHeight = 22.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("버튼을 눌러 녹음을 시작하세요!", fontSize = 13.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun PartialSubtitleCardContent(sentence: SentenceItem) {
    // TODO: 백엔드에서 partialContent 필드 추가 후 sentence.partialContent 사용
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
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start, color = Color(0xFF1A1A1A),
            lineHeight = 32.sp, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("버튼을 눌러 녹음을 시작하세요!", fontSize = 13.sp, color = Color(0xFF888888))
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int = 4, modifier: Modifier = Modifier) {
    val activeColor = Color(0xFFE53935)
    val inactiveColor = Color(0xFFDDDDDD)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isActive = index == currentStep

            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (isCompleted || isActive) activeColor else inactiveColor),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (index < totalSteps - 1) {
                Box(modifier = Modifier.weight(1f).height(2.dp).background(if (isCompleted) activeColor else inactiveColor))
            }
        }
    }
}