package com.bremenband.shadoweng.feature.mypage.presentation.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import com.bremenband.shadoweng.feature.mypage.domain.model.SessionSummary

@Composable
fun SessionListScreen(
    isReview: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToStudy: (sessionId: Long) -> Unit,
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sessions = if (isReview) state.completedSessions else state.activeSessions
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color(0xFF362000))
            }
            Text(
                if (isReview) "복습하기" else "이어하기",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFEDF57))
            }
            sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isReview) "복습할 세션이 없어요" else "진행 중인 학습이 없어요",
                    fontSize = 15.sp, color = Color(0xFF888888)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onNavigateToStudy(session.sessionId) },
                        onDelete = { deleteTargetId = session.sessionId }  // 바로 삭제 → 모달로 변경
                    )
                }
            }
        }
        if (deleteTargetId != null) {
            AlertDialog(
                onDismissRequest = { deleteTargetId = null },
                title = { Text("학습 삭제", fontWeight = FontWeight.Bold, color = Color(0xFF362000)) },
                text = { Text("이 학습 세션을 삭제하시겠어요?\n삭제 후 복구할 수 없어요.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTargetId?.let { viewModel.deleteSession(it) }
                        deleteTargetId = null
                    }) {
                        Text("삭제", color = Color(0xFFFF5D5D), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargetId = null }) {
                        Text("취소", color = Color(0xFF888888))
                    }
                }
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = session.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.thumbnail),
                    error = painterResource(R.drawable.thumbnail),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "삭제",
                        tint = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .padding(2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    session.title.ifEmpty { "학습 세션" },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000),
                    modifier = Modifier.weight(1f)
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${session.completedSentences}/${session.totalSentences} 문장",
                        fontSize = 12.sp, color = Color(0xFF888888)
                    )
                    LinearProgressIndicator(
                        progress = { session.progressRate / 100f },
                        modifier = Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (session.isCompleted) Color(0xFFFF5D5D) else Color(0xFFFEDF57),
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
            ) {
                Text(
                    if (session.isCompleted) "복습하기" else "이어서 학습하기",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000)
                )
            }
        }
    }
}