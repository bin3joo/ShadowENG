package com.bremenband.shadoweng.feature.study.domain

data class SentenceItem(
    val id: Long,
    val timestamp: String,
    val content: String,
    val isCompleted: Boolean = false
)