package com.bremenband.shadoweng.feature.content.util

object YoutubeUrlParser {
    private val patterns = listOf(
        Regex("(?:youtube\\.com/watch\\?v=)([a-zA-Z0-9_-]{11})"),
        Regex("(?:youtu\\.be/)([a-zA-Z0-9_-]{11})"),
        Regex("(?:youtube\\.com/embed/)([a-zA-Z0-9_-]{11})")
    )

    fun extractVideoId(url: String): String? =
        patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}