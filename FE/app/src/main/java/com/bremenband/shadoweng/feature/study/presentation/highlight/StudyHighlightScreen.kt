package com.bremenband.shadoweng.feature.study.presentation.highlight

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.ExpressionInfoCard
import com.bremenband.shadoweng.feature.study.presentation.component.StepIndicator

@Composable
fun StudyHighlightScreen(
    sessionId: Long,
    sentenceId: Long,
    onNextMode: () -> Unit,
    onSessionEnd: () -> Unit,
    onRetryRecording: () -> Unit,
    isLastMode: Boolean = false,
    viewModel: StudyHighlightViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sentenceId) { viewModel.init(sessionId, sentenceId) }
    LaunchedEffect(Unit) {
        viewModel.navigateToNextMode.collect {
            if (isLastMode) onSessionEnd() else onNextMode()
        }
    }
    LaunchedEffect(Unit) { viewModel.navigateRetry.collect { onRetryRecording() } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F0))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )

            StepIndicator(currentStep = 2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipButton(text = "1 x")
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(50))
                                .clickable { }.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = "반복", modifier = Modifier.size(16.dp), tint = Color(0xFF444444))
                        }
                        ChipButton(
                            text = "한글 자막 켜기",
                            isActive = uiState.showKoreanSubtitle,
                            onClick = { viewModel.onEvent(StudyHighlightEvent.ToggleKoreanSubtitle) }
                        )
                    }
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "북마크", modifier = Modifier.size(24.dp), tint = Color(0xFF888888))
                }

                Text(text = uiState.sentence, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), lineHeight = 30.sp)

                if (uiState.showKoreanSubtitle) {
                    Text(text = uiState.koreanTranslation, fontSize = 14.sp, color = Color(0xFF666666), lineHeight = 22.sp)
                }

                if (uiState.pronunciationFeedback.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF9F9F9)).padding(16.dp)) {
                        Text(text = uiState.pronunciationFeedback, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)
                    }
                }

                if (uiState.expressionDescription.isNotEmpty()) {
                    Text(text = uiState.expressionDescription, fontSize = 13.sp, color = Color(0xFF555555), lineHeight = 20.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(StudyHighlightEvent.RetryRecording) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("자막 보면서 다시 말하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                }
                Button(
                    onClick = { viewModel.onEvent(StudyHighlightEvent.NextMode) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500))
                ) {
                    Text(if (isLastMode) "다음 문장 학습" else "다음 단계로 넘어가기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                }
            }
        }

        uiState.expressionInfo?.let { info ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.BottomCenter) {
                ExpressionInfoCard(info = info, onDismiss = { viewModel.onEvent(StudyHighlightEvent.DismissExpression) })
            }
        }
    }
}

@Composable
private fun ChipButton(text: String, isActive: Boolean = false, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (isActive) Color(0xFFFEE500) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFFFEE500) else Color(0xFFDDDDDD), RoundedCornerShape(50))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 13.sp, color = Color(0xFF444444))
    }
}