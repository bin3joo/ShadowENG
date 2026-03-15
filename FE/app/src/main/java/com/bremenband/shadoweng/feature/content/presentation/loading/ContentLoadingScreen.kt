package com.bremenband.shadoweng.feature.content.presentation.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R

@Composable
fun ContentLoadingScreen(
    sessionId: Long,
    onNavigateToStudy: (sessionId: Long) -> Unit,
    viewModel: ContentLoadingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.navigateToStudy.collect { onNavigateToStudy(sessionId) } }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.engmu),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "콘텐츠 분석 중...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.steps.forEach { StepRow(it) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFFEE500),
                trackColor = Color(0xFFE0E0E0)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(uiState.progress * 100).toInt()}%",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
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