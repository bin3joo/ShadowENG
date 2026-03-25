package com.bremenband.shadoweng.feature.game.presentation.result

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.RainEffect
import com.bremenband.shadoweng.feature.game.domain.model.GameFinalResult
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameResultScreen(
    finalResult: GameFinalResult,
    hearts: Int,
    level: Int,
    maxLevel: Int = 3,
    onRetry: () -> Unit,
    onNextLevel: () -> Unit,
    onFinish: () -> Unit,
    previousBestScore: Double? = null,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToLevelSelect: () -> Unit

) {
    val isSuccess = hearts > 0

    val scoreAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400)
        scoreAnim.animateTo(
            targetValue = finalResult.avgTotalScore.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val heartScales = List(3) { remember { Animatable(if (it < hearts) 0f else 1f) } }
    LaunchedEffect(Unit) {
        heartScales.take(hearts).forEachIndexed { index, anim ->
            delay(600L + index * 150L)
            anim.animateTo(1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ))
        }
    }

    val cardOffsetY = remember { Animatable(200f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200)
        kotlinx.coroutines.coroutineScope {
            launch { cardOffsetY.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing)) }
            launch { cardAlpha.animateTo(1f, animationSpec = tween(600)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = if (isSuccess) R.drawable.background3 else R.drawable.background1),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // X 버튼 추가
        IconButton(
            onClick = onNavigateToLevelSelect,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "나가기",
                tint = Color(0xFF362000),
                modifier = Modifier.size(24.dp)
            )
        }

        // 비 효과 (실패 시)
        if (!isSuccess) RainEffect()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // 말풍선 — GamePlayScreen과 동일한 스타일
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(
                        text = if (isSuccess) "꼬시기 대성공! 잉무가 완전히 반했어요!"
                        else "잉무의 마음을 얻지 못했어요...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontSize = 14.sp,
                        color = Color(0xFF362000),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 잉무 이미지
            Image(
                painter = painterResource(id = R.drawable.engmu),
                contentDescription = "잉무",
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit
            )

            // 최고 점수 갱신 시
            val isNewRecord = previousBestScore != null && finalResult.avgTotalScore > previousBestScore

            if (isNewRecord) {
                val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
                    initialValue = 0.6f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "shimmerAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEDF57).copy(alpha = shimmer))
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🏆 최고 기록 갱신!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF362000)
                    )
                }
            }

            // 결과 카드 (슬라이드업)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .offset(y = cardOffsetY.value.dp)
                    .graphicsLayer { alpha = cardAlpha.value },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 하트 (팝 애니메이션)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        heartScales.forEachIndexed { index, anim ->
                            Image(
                                painter = painterResource(id = R.drawable.icon_heart),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(anim.value)
                                    .graphicsLayer { alpha = if (index < hearts) 1f else 0.25f }
                            )
                        }
                    }

                    // 종합 점수 (카운팅)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("종합 점수", fontSize = 13.sp, color = Color(0xFF888888))
                        Text(
                            "${scoreAnim.value.roundToInt()}",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSuccess) Color(0xFFFEDF57) else Color(0xFFFF5D5D)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ScoreChip("속도", finalResult.avgSpeedSimilarity.roundToInt())
                            ScoreChip("강세", finalResult.avgDynamicStressScore.roundToInt())
                            ScoreChip("억양", finalResult.avgBoundaryToneScore.roundToInt())
                        }
                    }

                    // 간단 피드백
                    if (finalResult.missedWords.isNotEmpty() ||
                        finalResult.boundaryToneStatus.isNotEmpty() ||
                        finalResult.dynamicStressStatus.isNotEmpty()
                    ) {
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFF8F0))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "이렇게 연습해보세요",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5D5D)
                            )
                            if (finalResult.missedWords.isNotEmpty()) {
                                val words = finalResult.missedWords.take(2).joinToString(", ")
                                FeedbackRow("🔤", "\"$words\" 발음을 더 명확하게 해보세요")
                            }
                            if (finalResult.boundaryToneStatus == "weak" || finalResult.boundaryToneStatus == "short") {
                                FeedbackRow("📈", "문장 끝을 조금 더 올려서 마무리해요")
                            }
                            if (finalResult.dynamicStressStatus == "monotone") {
                                FeedbackRow("🎵", "강약을 살려서 말해보세요")
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF0F0F0))

                    // 누적 점수 (작게)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("누적 점수", fontSize = 13.sp, color = Color(0xFF888888))
                        Text(
                            "%.0f점".format(finalResult.finalScore),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF362000)
                        )
                    }
                }
            }

            // 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSuccess) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("다시 하기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                    }
                    Button(
                        onClick = if (level >= maxLevel) onFinish else onNextLevel,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                    ) {
                        Text(
                            if (level >= maxLevel) "완료하기 🎉" else "Level ${level + 1} →",
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000)
                        )
                    }
                } else {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                    ) {
                        Text("Level $level 다시 도전하기", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackRow(emoji: String, message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 13.sp)
        Text(message, fontSize = 12.sp, color = Color(0xFF362000), lineHeight = 18.sp)
    }
}

@Composable
private fun ScoreChip(label: String, score: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF888888))
        Text("${score}점", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
    }
}