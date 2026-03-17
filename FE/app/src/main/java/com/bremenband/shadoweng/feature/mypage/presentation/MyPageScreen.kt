package com.bremenband.shadoweng.feature.mypage.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MyPageScreen(
    onNavigateToContent: (contentId: Long) -> Unit,
    viewModel: MyPageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToContent.collect { onNavigateToContent(it) }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE53935))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F3)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { DailySentenceCard(count = uiState.dailySentenceCount) }

        item { Text("학습 중인 컨텐츠", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        items(uiState.learningContents, key = { "content_${it.id}" }) { content ->
            LearningContentCard(
                content = content,
                onClick = { viewModel.onEvent(MyPageEvent.ClickContent(content.id)) }
            )
        }

        item { Text("북마크", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        items(uiState.bookmarks, key = { "bookmark_${it.id}" }) { bookmark ->
            BookmarkCard(item = bookmark)
        }
    }
}

@Composable
private fun DailySentenceCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 학습한 문장", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(" 문장", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text("📚", fontSize = 40.sp)
        }
    }
}

@Composable
private fun LearningContentCard(content: LearningContent, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(content.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { content.progress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFFEE500),
                    trackColor = Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "${content.completedSentences}/${content.totalSentences}",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

@Composable
private fun BookmarkCard(item: BookmarkItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.sentence, fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp)
            if (item.contentTitle.isNotEmpty()) {
                Text(item.contentTitle, fontSize = 11.sp, color = Color(0xFF888888))
            }
            // TODO: 백엔드 contentTitle 응답에 포함 요청
        }
    }
}