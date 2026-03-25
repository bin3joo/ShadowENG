package com.bremenband.shadoweng.core.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AudioPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = Color(0xFFFF5D5D)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio")

    // 재생 중일 때 ripple scale
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    // 파형 막대 3개 높이 애니메이션
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )

    Box(
        modifier = modifier.size(size + 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ripple (재생 중만)
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(rippleScale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = rippleAlpha))
            )
        }

        // 메인 버튼
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isPlaying) color else color.copy(alpha = 0.15f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                // 재생 중: 파형 막대 3개
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(size * 0.45f)
                ) {
                    listOf(bar1, bar2, bar3).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight(barHeight)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                }
            } else {
                // 정지: 삼각형 재생 아이콘
                PlayTriangle(color = color, size = size * 0.8f)
            }
        }
    }
}

@Composable
private fun PlayTriangle(color: Color, size: Dp) {
    Icon(
        imageVector = Icons.Rounded.PlayArrow,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}