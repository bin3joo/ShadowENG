package com.bremenband.shadoweng.feature.study.presentation.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.feature.study.domain.SentenceItem

@Composable
fun StudySessionScreen(
    sessionId: Long,
    onStartStudy: (sessionId: Long, sentenceId: Long) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: StudySessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToLearning.collect { onStartStudy(sessionId, it) }
    }
    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId) }

    val completedCount = uiState.sentences.count { it.isCompleted }
    val totalCount = uiState.sentences.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
    ) {
        // 상단 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F3))
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = Color(0xFF362000))
            }
            Text(
                text = "학습하기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 썸네일 카드 (그림자 없음)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column {
                        AsyncImage(
                            model = uiState.thumbnailUrl.ifEmpty { null },
                            contentDescription = "썸네일",
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.thumbnail),
                            error = painterResource(id = R.drawable.thumbnail),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parseVideoTitle(uiState.title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF362000),
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "$completedCount / $totalCount 문장",
                                    fontSize = 13.sp,
                                    color = Color(0xFF888888)
                                )
                                LinearProgressIndicator(
                                    progress = { if (totalCount > 0) completedCount / totalCount.toFloat() else 0f },
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFFEDF57),
                                    trackColor = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
            }

            // 문장 리스트 카드
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        uiState.sentences.forEachIndexed { index, sentence ->
                            val isUnlocked = sentence.isCompleted || index == 0 || uiState.sentences[index - 1].isCompleted
                            SentenceListItem(
                                item = sentence,
                                isUnlocked = isUnlocked,
                                onClick = {
                                    viewModel.onEvent(StudySessionEvent.SelectSentence(sentence.id))
                                }
                            )
                            if (index < uiState.sentences.lastIndex) {
                                HorizontalDivider(
                                    color = Color(0xFFF5F5F3),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// SentenceListItem 파라미터 추가
@Composable
private fun SentenceListItem(
    item: SentenceItem,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    val isCompleted = item.isCompleted
    val isClickable = !isCompleted && isUnlocked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isCompleted) Color(0xFFFF5D5D) else Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else {
                Text(text = item.timestamp, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))
            }
        }

        Text(
            text = item.content,
            fontSize = 14.sp,
            color = when {
                isCompleted -> Color(0xFFBBBBBB)
                !isUnlocked -> Color(0xFFCCCCCC)
                else -> Color(0xFF1A1A1A)
            },
            modifier = Modifier.weight(1f),
            lineHeight = 22.sp
        )

        when {
            isCompleted -> Spacer(modifier = Modifier.size(18.dp))
            isClickable -> Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
            else -> Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun parseVideoTitle(title: String): String {
    return title
        .replace(Regex("@\\S+"), "")
        .replace(Regex("#\\S+"), "")
        .trim()
}