package com.bremenband.shadoweng.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YoutubePlayerView(
    embedUrl: String,
    startSec: Double? = null,
    endSec: Double? = null,
    repeatCount: Int? = null,
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val videoId = remember(embedUrl) { extractVideoId(embedUrl) }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (videoId.isEmpty()) return

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)

                    val options = IFramePlayerOptions.Builder()
                        .controls(1)
                        .autoplay(0)
                        .origin("https://com.bremenband.shadoweng")
                        .build()

                    initialize(object : AbstractYouTubePlayerListener() {
                        private var loopCount = 0
                        private var isSeeking = false
                        private var hasReachedEnd = false

                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            val start = startSec?.toFloat() ?: 0f
                            if (autoPlay) youTubePlayer.loadVideo(videoId, start)
                            else youTubePlayer.cueVideo(videoId, start)
                        }
                        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                            if (endSec == null || isSeeking) return

                            if (second >= endSec.toFloat() && !hasReachedEnd) {
                                hasReachedEnd = true
                                youTubePlayer.pause()

                                val shouldLoop = repeatCount == null || loopCount < repeatCount - 1
                                if (shouldLoop) {
                                    loopCount++
                                }
                            }
                        }

                        override fun onStateChange(
                            youTubePlayer: YouTubePlayer,
                            state: PlayerConstants.PlayerState
                        ) {
                            when (state) {
                                PlayerConstants.PlayerState.PLAYING -> {
                                    isSeeking = false
                                    hasReachedEnd = false
                                }

                                PlayerConstants.PlayerState.PAUSED -> {
                                    // 사용자가 재생 버튼을 누르면 시작 위치로 리셋
                                    if (hasReachedEnd) {
                                        val start = startSec?.toFloat() ?: 0f
                                        isSeeking = true
                                        youTubePlayer.seekTo(start)
                                    }
                                }

                                else -> {}
                            }
                        }
                    }, options)
                }
            },
            update = { },
            onRelease = { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
        )

        // 상단 오버레이
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.2f)
                .align(Alignment.TopCenter)
//                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

// 하단 오버레이
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.3f)
                .align(Alignment.BottomCenter)
//                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

// 좌측 오버레이
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterStart)
//                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

// 우측 오버레이
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterEnd)
//                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures { } }
        )
    }
}

private fun extractVideoId(embedUrl: String): String {
    val vParam = Regex("[?&]v=([^&]+)").find(embedUrl)?.groupValues?.get(1)
    if (!vParam.isNullOrEmpty()) return vParam
    val shortUrl = Regex("youtu\\.be/([^?]+)").find(embedUrl)?.groupValues?.get(1)
    if (!shortUrl.isNullOrEmpty()) return shortUrl
    val embedPath = Regex("embed/([^?]+)").find(embedUrl)?.groupValues?.get(1)
    if (!embedPath.isNullOrEmpty()) return embedPath
    val liveUrl = Regex("youtube\\.com/live/([^?]+)").find(embedUrl)?.groupValues?.get(1)
    if (!liveUrl.isNullOrEmpty()) return liveUrl
    return ""
}