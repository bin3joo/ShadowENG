package com.bremenband.shadoweng.feature.mypage.presentation.bookmark

import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence

data class BookmarkState(
    val bookmarks: List<BookmarkedSentence> = emptyList(),
    val isLoading: Boolean = true
)