package com.bremenband.shadoweng.core.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioRecordButton(
    isRecording: Boolean,
    enabled: Boolean = true,
    onStartCountdown: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            RecordingRipple(color = Color(0xFFE53935))
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(
                    when {
                        isRecording -> Color(0xFFE53935)
                        enabled -> Color(0xFFF28B8B)
                        else -> Color(0xFFCCCCCC)
                    }
                )
                .clickable(enabled = enabled) {
                    if (isRecording) onStopRecording() else onStartCountdown()
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isRecording,
                animationSpec = tween(200),
                label = "iconCrossfade"
            ) { recording ->
                Icon(
                    imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
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
        Box(modifier = Modifier.size(72.dp).scale(scale1).clip(CircleShape).background(color.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(72.dp).scale(scale2).clip(CircleShape).background(color.copy(alpha = alpha2)))
    }
}