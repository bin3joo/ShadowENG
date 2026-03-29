package com.bremenband.shadoweng.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

@Composable
fun ThumbnailImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = url?.ifEmpty { null },
        contentDescription = "썸네일",
        contentScale = contentScale,
        modifier = modifier,
        loading = { ShimmerBox(modifier = Modifier.fillMaxSize()) },
        error = { ShimmerBox(modifier = Modifier.fillMaxSize(), isError = true) }
    )
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier, isError: Boolean = false) {
    if (isError) {
        Box(modifier = modifier.background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) {
            Text("썸네일 없음", fontSize = 11.sp, color = Color(0xFFAAAAAA))
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        Color(0xFFE0E0E0),
        Color(0xFFF5F5F5),
        Color(0xFFE0E0E0)
    )

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translateAnim - 300f, 0f),
                end = Offset(translateAnim, 0f)
            )
        )
    )
}