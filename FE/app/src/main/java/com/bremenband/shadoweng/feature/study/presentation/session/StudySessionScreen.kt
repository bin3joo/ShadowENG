package com.bremenband.shadoweng.feature.study.presentation.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.feature.study.domain.SentenceItem

@Composable
fun StudySessionScreen(
    sessionId: Long,
    onStartStudy: (sentenceId: Long) -> Unit,
    viewModel: StudySessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToLearning.collect { onStartStudy(it) }
    }
    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Text(
            text = "학습 세션",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(id = R.drawable.thumbnail),
            contentDescription = "썸네일",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "${uiState.sentences.count { it.isCompleted }} / ${uiState.sentences.size} 문장",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.sentences, key = { "sentence_${it.id}" }) { sentence ->
                SentenceListItem(
                    item = sentence,
                    onClick = { viewModel.onEvent(StudySessionEvent.SelectSentence(sentence.id)) }
                )
                HorizontalDivider(
                    color = Color(0xFFEEEEEE),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SentenceListItem(item: SentenceItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (item.isCompleted) Color(0xFFE53935) else Color(0xFFE8E8E8)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = item.timestamp,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.width(44.dp)
        )

        Text(
            text = item.content,
            fontSize = 15.sp,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.weight(1f),
            lineHeight = 22.sp
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(22.dp)
        )
    }
}