package com.bremenband.shadoweng.feature.mypage.presentation.bookmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.feature.mypage.domain.model.BookmarkedSentence

@Composable
fun BookmarkScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BookmarkViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFEDF57))
        }
        return
    }

    if (state.bookmarks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("북마크한 문장이 없어요", fontSize = 15.sp, color = Color(0xFF888888))
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "뒤로",
                        tint = Color(0xFF362000)
                    )
                }
                Text(
                    "북마크",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF362000),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.bookmarks) { bookmark ->
                    BookmarkItem(bookmark = bookmark)
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
        }
    }
}

@Composable
private fun BookmarkItem(bookmark: BookmarkedSentence) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = Color(0xFF362000),
            modifier = Modifier.size(20.dp)
        )
        Text(
            bookmark.sentence,
            fontSize = 14.sp,
            color = Color(0xFF1A1A1A),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFBBBBBB),
            modifier = Modifier.size(18.dp)
        )
    }
}