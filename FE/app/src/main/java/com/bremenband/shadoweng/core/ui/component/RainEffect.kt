package com.bremenband.shadoweng.core.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class RainDrop(
    val x: Float,
    val startY: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float
)

@Composable
fun RainEffect(modifier: Modifier = Modifier) {
    val drops = remember {
        List(60) {
            RainDrop(
                x = Random.nextFloat(),
                startY = Random.nextFloat() * -1f,
                speed = 0.3f + Random.nextFloat() * 0.4f,
                length = 0.02f + Random.nextFloat() * 0.03f,
                alpha = 0.3f + Random.nextFloat() * 0.4f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drops.forEach { drop ->
            val y = ((drop.startY + progress * drop.speed * 3f) % 1.2f)
            if (y < 0f) return@forEach

            val startOffset = Offset(
                x = drop.x * size.width,
                y = y * size.height
            )
            val endOffset = Offset(
                x = drop.x * size.width - drop.length * size.width * 0.2f,
                y = (y + drop.length) * size.height
            )

            drawLine(
                color = Color(0xFF88AACC).copy(alpha = drop.alpha),
                start = startOffset,
                end = endOffset,
                strokeWidth = 6f
            )
        }
    }
}