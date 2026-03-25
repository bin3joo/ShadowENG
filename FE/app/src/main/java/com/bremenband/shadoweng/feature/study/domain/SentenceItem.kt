package com.bremenband.shadoweng.feature.study.domain

data class SentenceItem(
    val id: Long,
    val timestamp: String,
    val content: String,
    val isCompleted: Boolean = false,
    val startSec: Double = 0.0,
    val endSec: Double = 0.0
)