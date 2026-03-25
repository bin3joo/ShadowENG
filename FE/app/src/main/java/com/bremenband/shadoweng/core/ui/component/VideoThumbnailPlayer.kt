package com.bremenband.shadoweng.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R

@Composable
fun VideoThumbnailPlayer(
    embedUrl: String,
    thumbnailUrl: String,
    startSec: Double,
    endSec: Double,
    modifier: Modifier = Modifier,
    repeatCount: Int = 1
) {
    var isPlaying by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        // WebView는 항상 렌더링, isPlaying 아닐 때 크기 0으로 숨김
//        YoutubePlayerView(
//            embedUrl = embedUrl,
//            startSec = startSec,
//            endSec = endSec,
//            repeatCount = repeatCount,
//            modifier = if (isPlaying) Modifier.fillMaxSize() else Modifier.size(0.dp)
//        )

        if (!isPlaying) {
            AsyncImage(
                model = thumbnailUrl.ifEmpty { null },
                contentDescription = "썸네일",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.thumbnail),
                error = painterResource(id = R.drawable.thumbnail),
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .align(Alignment.Center)
            ) {
                IconButton(
                    onClick = { isPlaying = true },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "재생",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}