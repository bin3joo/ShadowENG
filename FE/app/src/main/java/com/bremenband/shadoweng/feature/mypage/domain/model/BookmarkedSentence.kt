package com.bremenband.shadoweng.feature.mypage.domain.model

data class BookmarkedSentence(
    val sentenceId: Long,
    val sentence: String,
    val sessionId: Long
)