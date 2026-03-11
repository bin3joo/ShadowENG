package com.bremenband.shadoweng.feature.content.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bremenband.shadoweng.feature.content.model.ContentRangeEvent
import com.bremenband.shadoweng.feature.content.viewmodel.ContentRangeViewModel

@Composable
fun ContentRangeScreen(
    embedUrl: String,
    onNavigateToStudy: (sessionId: Long) -> Unit,
    viewModel: ContentRangeViewModel = hiltViewModel()
) {
    LaunchedEffect(embedUrl) { viewModel.init(embedUrl) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToStudy.collect { sessionId -> onNavigateToStudy(sessionId) }
    }

    val isSubmitEnabled = uiState.isStartValid && uiState.isEndValid && !uiState.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text("학습 구간을 설정해 주세요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("학습을 원하는 구간을 설정해 주세요", fontSize = 14.sp, color = Color.Gray)

        // 영상 미리보기
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // TODO: 실제 유튜브 플레이어 연결
            Text("▶", color = Color.White, fontSize = 48.sp)
        }

        // 시작/종료 시간 입력
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.startTime,
                onValueChange = { viewModel.onEvent(ContentRangeEvent.StartTimeChanged(it)) },
                label = { Text("시작") },
                placeholder = { Text("00:00") },
                modifier = Modifier.weight(1f),
                isError = uiState.startTime.isNotEmpty() && !uiState.isStartValid,
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.endTime,
                onValueChange = { viewModel.onEvent(ContentRangeEvent.EndTimeChanged(it)) },
                label = { Text("종료") },
                placeholder = { Text("00:00") },
                modifier = Modifier.weight(1f),
                isError = uiState.endTime.isNotEmpty() && !uiState.isEndValid,
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Text("형식: MM:SS 또는 HH:MM:SS", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.onEvent(ContentRangeEvent.Submit) },
            enabled = isSubmitEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("학습 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}