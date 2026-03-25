package com.bremenband.shadoweng.feature.study.domain

data class StudySession(
    val sessionId: Long,
    val videoId: String,
    val embedUrl: String,
    val watchUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val sentences: List<SentenceItem>
)