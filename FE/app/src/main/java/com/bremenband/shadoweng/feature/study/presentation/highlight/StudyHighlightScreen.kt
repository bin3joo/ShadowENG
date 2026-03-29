package com.bremenband.shadoweng.feature.study.presentation.highlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.core.ui.component.AnnotatedSentenceView
import com.bremenband.shadoweng.core.ui.component.ExitConfirmDialog
import com.bremenband.shadoweng.core.ui.component.model.ExpressionInfo
import com.bremenband.shadoweng.feature.study.api.dto.VocabularyDto
import com.bremenband.shadoweng.feature.study.domain.EvaluationResult
import com.bremenband.shadoweng.feature.study.presentation.component.StepIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch

@Composable
fun StudyHighlightScreen(
    sessionId: Long,
    sentenceId: Long,
    step: Int,
    onNextMode: () -> Unit,
    onNextSentence: (sentenceId: Long) -> Unit,
    onExit: () -> Unit,
    onSessionEnd: (reportId: Long) -> Unit,
    onRetryRecording: () -> Unit,
    viewModel: StudyHighlightViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLastStep = step == 4

    LaunchedEffect(sentenceId, step) { viewModel.init(sessionId, sentenceId, step) }
    LaunchedEffect(Unit) { viewModel.navigateToNextMode.collect { onNextMode() } }
    LaunchedEffect(Unit) { viewModel.navigateToSessionEnd.collect { onSessionEnd(it) } }
    LaunchedEffect(Unit) { viewModel.navigateRetry.collect { onRetryRecording() } }
    LaunchedEffect(Unit) { viewModel.navigateToNextSentence.collect { onNextSentence(it) } }

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        ExitConfirmDialog(
            message = "지금 학습을 중단하면 기록이 저장되지 않아요.\n정말 중단하시겠어요?",
            onConfirm = { onExit() },
            onDismiss = { showExitDialog = false }
        )
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF5F5F0))) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.embedUrl.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF362000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("▶", fontSize = 12.sp, color = Color(0xFFFEDF57))
                    Text(
                        text = "영상 구간 ${formatSec(uiState.startSec)} ~ ${formatSec(uiState.endSec)}",
                        fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium
                    )
                }
            }

//            StepIndicator(
//                currentStep = step,
//                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
//            )
            Spacer(modifier = Modifier.height(14.dp))


            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.sentence.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.annotations.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AnnotationLegend()
                                Text(
                                    "색상 단어를 탭하면 피드백을 확인할 수 있어요",
                                    fontSize = 11.sp,
                                    color = Color(0xFFAAAAAA)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Surface(
                            onClick = { viewModel.onEvent(StudyHighlightEvent.ToggleKoreanSubtitle) },
                            shape = RoundedCornerShape(50),
                            color = if (uiState.showKoreanSubtitle) Color(0xFFFEDF57) else Color(
                                0xFFF0F0F0
                            )
                        ) {
                            Text(
                                "한글 자막 켜기",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF362000),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AnnotatedSentenceView(
                        sentence = uiState.sentence,
                        annotations = uiState.annotations,
                        onWordTap = { word, idx ->
                            viewModel.onEvent(
                                StudyHighlightEvent.TapWord(
                                    word,
                                    idx
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = uiState.showKoreanSubtitle && uiState.sentenceKo.isNotEmpty()) {
                        Box(modifier = Modifier.padding(top = 0.dp)) {
                            Text(
                                uiState.sentenceKo,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                uiState.evaluationResult?.let { result ->
                    SentenceFeedbackCard(result = result, step = step)
                }

                if (step == 2) {
                    MoreInfoToggleCard(
                        vocabulary = uiState.vocabulary,
                        sentenceKo = uiState.sentenceKo
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(StudyHighlightEvent.RetryRecording) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "다시 말하기",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                Button(
                    onClick = {
                        if (isLastStep) viewModel.onEvent(StudyHighlightEvent.SessionEnd)
                        else viewModel.onEvent(StudyHighlightEvent.NextMode)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                ) {
                    Text(
                        when {
                            !isLastStep -> "다음 단계로 넘어가기"
                            uiState.isLastSentence -> "결과 보기"
                            else -> "다음 문장 학습"
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.expressionInfoList.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { viewModel.onEvent(StudyHighlightEvent.DismissExpression) },
                contentAlignment = Alignment.BottomCenter
            ) {
                WordFeedbackCard(
                    infoList = uiState.expressionInfoList,
                    onDismiss = { viewModel.onEvent(StudyHighlightEvent.DismissExpression) },
                    modifier = Modifier.clickable { }
                )
            }
        }
    }
}

@Composable
private fun SentenceFeedbackCard(result: EvaluationResult, step: Int) {
    var showDetailModal by remember { mutableStateOf(false) }

    if (showDetailModal) {
        ScoreDetailModal(result = result, onDismiss = { showDetailModal = false })
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF9F9F9))
            .then(if (step == 4) Modifier.clickable { showDetailModal = true } else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("문장 피드백", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        if (step == 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("종합 점수", fontSize = 13.sp, color = Color(0xFF888888))
                Text(
                    "${result.scores.totalScore.toInt()}점",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF5D5D)
                )
            }
            Text(
                "탭하면 세부 점수를 볼 수 있어요",
                fontSize = 11.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            FeedbackStatusRow(label = "문장 끝 억양", status = result.boundaryToneFeedback.status)
            FeedbackStatusRow(label = "강세 조절", status = result.dynamicStressFeedback.status)
        }
    }
}
@Composable
private fun ScoreBar(label: String, score: Double, feedback: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
            Text(
                "${score.toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000)
            )
        }
        LinearProgressIndicator(
            progress = { (score / 100f).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFFEDF57), trackColor = Color(0xFFE0E0E0)
        )
        Text(
            feedback, fontSize = 12.sp, color = Color(0xFF888888),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun speedFeedback(score: Double) = when {
    score >= 90 -> "원본 음성과 속도가 매우 비슷해요"
    score >= 70 -> "원본 음성보다 조금 빠르게 말했어요"
    score >= 50 -> "속도 차이가 있어요. 조금 더 맞춰보세요"
    else -> "속도를 원본에 맞게 조절해보세요"
}

private fun stressFeedback(status: String) = when (status) {
    "good" -> "주요 단어를 명확히 표현했어요"
    "monotone" -> "강약을 더 살려서 말해보세요"
    "exaggerated" -> "강세를 조금 줄여보세요"
    "weak" -> "더 강하게 발음해보세요"
    else -> "강세 조절을 연습해보세요"
}

private fun intonationFeedback(status: String) = when (status) {
    "good" -> "억양이 자연스러워요"
    "weak" -> "문장 끝을 조금 더 올려서 마무리해요"
    "short" -> "억양의 높낮이 변화가 너무 컸어요"
    "opposite" -> "억양 방향을 반대로 해보세요"
    else -> "억양을 자연스럽게 조절해보세요"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreInfoToggleCard(vocabulary: List<VocabularyDto>, sentenceKo: String) {
    if (vocabulary.isEmpty() && sentenceKo.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { vocabulary.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFBF0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "💡 더 알아보기",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000)
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HorizontalDivider(color = Color(0xFFEEDDB0))

            if (vocabulary.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${vocabulary[pagerState.currentPage].word} · ${vocabulary[pagerState.currentPage].meaning_ko}",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    val vocab = vocabulary[page]
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${vocab.phonetic_ko} / ${vocab.phonetic_en}",
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                        Text(vocab.example_en, fontSize = 13.sp, color = Color(0xFF1A1A1A))
                        Text(vocab.example_ko, fontSize = 13.sp, color = Color(0xFF888888))
                    }
                }

                if (vocabulary.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(vocabulary.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == index) 7.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) Color(
                                            0xFF362000
                                        ) else Color(0xFFCCCCCC)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordFeedbackCard(
    infoList: List<ExpressionInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (infoList.isEmpty()) return
    val info = infoList.first()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    info.word,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF362000)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color(0xFF888888))
                }
            }
            if (info.pronunciation.isNotEmpty()) {
                Text(info.pronunciation, fontSize = 13.sp, color = Color(0xFF888888))
            }
            Text(info.description, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)
            if (info.examples.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.examples.forEach { example ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                            Text(
                                example,
                                fontSize = 13.sp,
                                color = Color(0xFF666666),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackStatusRow(label: String, status: String) {
    val (emoji, message) = when (status) {
        "good" -> "✅" to "좋아요!"
        "short" -> "📉" to "조금 더 올려서 마무리해요"
        "monotone" -> "📊" to "강약을 더 살려보세요"
        "exaggerated" -> "📢" to "강세를 조금 줄여보세요"
        "weak" -> "🔉" to "더 강하게 발음해보세요"
        else -> "💬" to status
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 13.sp)
        Text(label, fontSize = 13.sp, color = Color(0xFF666666))
        Text("·", fontSize = 13.sp, color = Color(0xFFCCCCCC))
        Text(message, fontSize = 13.sp, color = Color(0xFF444444))
    }
}

private fun scoreColor(score: Double) = when {
    score >= 80 -> Color(0xFF1DB954)
    score >= 60 -> Color(0xFFFFB800)
    else -> Color(0xFFFF5D5D)
}

private fun formatSec(sec: Double): String {
    val m = (sec / 60).toInt()
    val s = (sec % 60).toInt()
    return "%d:%02d".format(m, s)
}

@Composable
private fun AnnotationLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = Color(0xFFFFB800), label = "길게", isWave = true)
        LegendItem(color = Color(0xFF1565C0), label = "짧게", isChevron = true)
        LegendItem(color = Color(0xFFFF5D5D), label = "누락", isBackground = true)
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isBackground: Boolean = false,
    isWave: Boolean = false,
    isChevron: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            isBackground -> Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 12.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            )

            isWave -> Canvas(modifier = Modifier.size(width = 20.dp, height = 12.dp)) {
                val mid = size.height / 2f
                drawPath(
                    Path().apply {
                        moveTo(0f, mid); cubicTo(
                        4.dp.toPx(),
                        mid - 5.dp.toPx(),
                        8.dp.toPx(),
                        mid + 5.dp.toPx(),
                        12.dp.toPx(),
                        mid
                    )
                    },
                    color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            isChevron -> Canvas(modifier = Modifier.size(width = 20.dp, height = 12.dp)) {
                val mid = size.height / 2f;
                val cx = size.width / 2f
                drawPath(
                    Path().apply {
                        moveTo(cx - 5.dp.toPx(), mid - 4.dp.toPx()); lineTo(
                        cx,
                        mid + 4.dp.toPx()
                    ); lineTo(cx + 5.dp.toPx(), mid - 4.dp.toPx())
                    },
                    color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        Text(label, fontSize = 11.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun ScoreDetailModal(result: EvaluationResult, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "종합 점수",
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    "${result.scores.totalScore.toInt()}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF5D5D),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("단어 정확도", result.scores.wordAccuracy, speedFeedback(result.scores.wordAccuracy))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("억양/강세 종합", result.scores.prosodyAndStress, stressFeedback(result.dynamicStressFeedback.status))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("단어 리듬", result.scores.wordRhythmScore, intonationFeedback(result.boundaryToneFeedback.status))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("문장 끝 억양", result.scores.boundaryToneScore, intonationFeedback(result.boundaryToneFeedback.status))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("강세 변화", result.scores.dynamicStressScore, stressFeedback(result.dynamicStressFeedback.status))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("속도 유사도", result.scores.speedSimilarity, speedFeedback(result.scores.speedSimilarity))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                ScoreBar("포즈 유사도", result.scores.pauseSimilarity, speedFeedback(result.scores.pauseSimilarity))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                ) {
                    Text("확인", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                }
            }
        }
    }
}