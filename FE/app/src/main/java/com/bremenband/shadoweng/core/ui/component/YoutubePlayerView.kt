package com.bremenband.shadoweng.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YoutubePlayerView(
    embedUrl: String,
    startSec: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val videoId = remember(embedUrl) { extractVideoId(embedUrl) }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (videoId.isEmpty()) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView(context).apply {
                // 1. 초기 설정
                enableAutomaticInitialization = false

                // 2. 라이프사이클 연결 (factory 내에서 등록)
                lifecycleOwner.lifecycle.addObserver(this)

                val options = IFramePlayerOptions.Builder()
                    .controls(1)
                    .autoplay(1)
                    .build()

                // 3. 수동 초기화
                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        // 초기 로드
                        youTubePlayer.loadVideo(videoId, startSec.toFloat())
                    }
                }, options)
            }
        },
        update = { view ->
            // embedUrl이 바뀌었을 때 영상만 교체하는 로직 (필요시)
            // 주의: 초기화 완료(onReady) 후에만 호출 가능하므로 내부 상태 관리가 필요할 수 있습니다.
        },
        onRelease = { view ->
            // 4. 메모리 해제 및 옵저버 제거
            lifecycleOwner.lifecycle.removeObserver(view)
            view.release()
        }
    )
}

private fun extractVideoId(embedUrl: String): String {
    val vParam = Regex("[?&]v=([^&]+)").find(embedUrl)?.groupValues?.get(1)
    if (!vParam.isNullOrEmpty()) return vParam
    val shortUrl = Regex("youtu\\.be/([^?]+)").find(embedUrl)?.groupValues?.get(1)
    if (!shortUrl.isNullOrEmpty()) return shortUrl
    val embedPath = Regex("embed/([^?]+)").find(embedUrl)?.groupValues?.get(1)
    if (!embedPath.isNullOrEmpty()) return embedPath
    return ""
}