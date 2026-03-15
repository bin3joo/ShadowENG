package com.bremenband.shadoweng.core.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bremenband.shadoweng.R

@Composable
fun AudioRecordButton(
    isRecording: Boolean,
    countdown: Int?,
    onStartCountdown: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            countdown != null -> {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "$countdown", fontSize = 32.sp, color = Color.White)
                }
            }
            isRecording -> {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RecordingRipple(color = Color(0xFFE53935))
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable { onStopRecording() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "정지",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF28B8B))  // 연한 빨간색
                        .clickable { onStartCountdown() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "녹음",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleRipple(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "idle_ripple")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
    )
}

@Composable
private fun RecordingRipple(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_ripple")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "ripple1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "alpha1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(1000, delayMillis = 400), RepeatMode.Restart),
        label = "ripple2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, delayMillis = 400), RepeatMode.Restart),
        label = "alpha2"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale1)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha1))
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale2)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha2))
        )
    }
}