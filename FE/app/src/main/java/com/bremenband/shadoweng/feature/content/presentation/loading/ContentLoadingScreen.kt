package com.bremenband.shadoweng.feature.content.presentation.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import kotlinx.coroutines.delay

@Composable
fun ContentLoadingScreen(
    embedUrl: String,
    startSec: Double,
    endSec: Double,
    onNavigateToStudy: (sessionId: Long) -> Unit,
    viewModel: ContentLoadingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.navigateToStudy.collect { onNavigateToStudy(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.init(embedUrl, startSec, endSec)
    }

    val uiState by viewModel.uiState.collectAsState()

    // Bounce 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -24f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0 with FastOutSlowInEasing
                -24f at 500 with FastOutSlowInEasing
                0f at 800 with FastOutSlowInEasing
                0f at 1000  // 잠깐 바닥에 머무는 느낌
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bounceY"
    )

    val shadowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 with FastOutSlowInEasing
                0.55f at 500 with FastOutSlowInEasing
                1f at 800 with FastOutSlowInEasing
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shadowScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 잉무 + 그림자
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .size(width = 200.dp, height = 200.dp)
        ) {
            // 그림자
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .scale(shadowScale)
                    .size(width = 80.dp, height = 12.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000))
            )

            // 잉무
            Image(
                painter = painterResource(id = R.drawable.engmu),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = offsetY.dp)
                    .padding(bottom = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedLoadingText()

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFFFDF5))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${uiState.currentTip.emoji} ${uiState.currentTip.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF5D5D)
                )
                Text(
                    text = uiState.currentTip.content,
                    fontSize = 14.sp,
                    color = Color(0xFF362000),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun StepRow(stepState: ContentAnalysisStepState) {
    val (bgColor, iconColor, textColor, borderColor) = when (stepState.status) {
        ContentStepStatus.DONE -> listOf(
            Color(0xFFFFF0F0), Color(0xFFE53935), Color(0xFFE53935), Color(0xFFFFCDD2)
        )
        ContentStepStatus.IN_PROGRESS -> listOf(
            Color(0xFFFFFDE7), Color(0xFFFDD835), Color(0xFFF9A825), Color(0xFFFFF9C4)
        )
        ContentStepStatus.PENDING -> listOf(
            Color(0xFFF5F5F5), Color(0xFFBBBBBB), Color(0xFFBBBBBB), Color(0xFFE0E0E0)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            when (stepState.status) {
                ContentStepStatus.DONE ->
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                ContentStepStatus.IN_PROGRESS -> SpinningIndicator()
                ContentStepStatus.PENDING -> SpinningIndicator(tint = Color(0xFF888888))
            }
        }

        Text(
            text = stepState.step.label,
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (stepState.status != ContentStepStatus.PENDING) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SpinningIndicator(tint: Color = Color.White) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "angle"
    )
    Text(
        "◌",
        color = tint,
        fontSize = 14.sp,
        modifier = Modifier.graphicsLayer { rotationZ = angle }
    )
}

@Composable
private fun AnimatedLoadingText() {
    var dotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    Text(
        text = "콘텐츠 분석 중" + ".".repeat(dotCount),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1A1A1A)
    )
}

//@Composable
//private fun AnimatedLoadingText() {
//    val infiniteTransition = rememberInfiniteTransition(label = "dots")
//
//    val dot1Y by infiniteTransition.animateFloat(
//        initialValue = 0f, targetValue = -8f,
//        animationSpec = infiniteRepeatable(
//            animation = keyframes {
//                durationMillis = 900
//                0f at 0 with FastOutSlowInEasing
//                -8f at 200 with FastOutSlowInEasing
//                0f at 400 with FastOutSlowInEasing
//                0f at 900
//            },
//            repeatMode = RepeatMode.Restart
//        ), label = "dot1"
//    )
//    val dot2Y by infiniteTransition.animateFloat(
//        initialValue = 0f, targetValue = -8f,
//        animationSpec = infiniteRepeatable(
//            animation = keyframes {
//                durationMillis = 900
//                0f at 150 with FastOutSlowInEasing
//                -8f at 350 with FastOutSlowInEasing
//                0f at 550 with FastOutSlowInEasing
//                0f at 900
//            },
//            repeatMode = RepeatMode.Restart
//        ), label = "dot2"
//    )
//    val dot3Y by infiniteTransition.animateFloat(
//        initialValue = 0f, targetValue = -8f,
//        animationSpec = infiniteRepeatable(
//            animation = keyframes {
//                durationMillis = 900
//                0f at 300 with FastOutSlowInEasing
//                -8f at 500 with FastOutSlowInEasing
//                0f at 700 with FastOutSlowInEasing
//                0f at 900
//            },
//            repeatMode = RepeatMode.Restart
//        ), label = "dot3"
//    )
//
//    Row(
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.spacedBy(2.dp)
//    ) {
//        Text(
//            "콘텐츠 분석 중",
//            fontSize = 20.sp,
//            fontWeight = FontWeight.Bold,
//            color = Color(0xFF1A1A1A)
//        )
//        listOf(dot1Y, dot2Y, dot3Y).forEach { dotY ->
//            Text(
//                ".",
//                fontSize = 20.sp,
//                fontWeight = FontWeight.Bold,
//                color = Color(0xFF1A1A1A),
//                modifier = Modifier.offset(y = dotY.dp)
//            )
//        }
//    }
//}