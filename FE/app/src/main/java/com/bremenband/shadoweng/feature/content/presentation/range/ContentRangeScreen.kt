package com.bremenband.shadoweng.feature.content.presentation.range

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R

@Composable
fun ContentRangeScreen(
    embedUrl: String,
    onNavigateToStudy: (sessionId: Long) -> Unit,
    viewModel: ContentRangeViewModel = hiltViewModel()
) {
    LaunchedEffect(embedUrl) { viewModel.init(embedUrl) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToStudy.collect { onNavigateToStudy(it) }
    }

    val isRangeExceeded = uiState.isStartValid && uiState.isEndValid &&
            (parseTimeToSeconds(uiState.endTime) - parseTimeToSeconds(uiState.startTime)) > 1200

    val isSubmitEnabled = uiState.isStartValid && uiState.isEndValid &&
            !uiState.isLoading && !isRangeExceeded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "학습하고 싶은 부분을\n선택해주세요!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 썸네일 영역 (placeholder)
        Image(
            painter = painterResource(id = R.drawable.thumbnail),
            contentDescription = "썸네일",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        )
        // TODO: Coil로 유튜브 썸네일 로딩 (https://img.youtube.com/vi/{videoId}/0.jpg)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "동영상 재생 후 시작 / 종료 버튼을 누르면\n해당 타임스탬프가 자동으로 입력돼요.",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 시작/종료 입력
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top
        ) {
            TimeInputColumn(
                label = "시작",
                value = uiState.startTime,
                isError = uiState.startTime.isNotEmpty() && !uiState.isStartValid,
                onValueChange = { viewModel.onEvent(ContentRangeEvent.StartTimeChanged(it)) }
            )
            TimeInputColumn(
                label = "종료",
                value = uiState.endTime,
                isError = uiState.endTime.isNotEmpty() && !uiState.isEndValid,
                onValueChange = { viewModel.onEvent(ContentRangeEvent.EndTimeChanged(it)) }
            )
        }

        if (isRangeExceeded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "최대 20분까지 지정 가능해요!",
                color = Color(0xFFE53935),
                fontSize = 13.sp
            )
        }

        uiState.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = Color(0xFFE53935), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.onEvent(ContentRangeEvent.Submit) },
            enabled = isSubmitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                disabledContainerColor = Color(0xFFE0E0E0)
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color(0xFF1A1A1A), modifier = Modifier.size(24.dp))
            } else {
                Text("이 영상으로 학습 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TimeInputColumn(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { /* WebView 연결 전 placeholder */ },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A1A1A)),
            modifier = Modifier.width(80.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("0:00", color = Color.LightGray) },
            modifier = Modifier.width(80.dp),
            isError = isError,
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFDDDDDD),
                focusedBorderColor = Color(0xFF1A1A1A)
            )
        )
    }
}

private fun parseTimeToSeconds(time: String): Int {
    val parts = time.split(":").map { it.toIntOrNull() ?: 0 }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> 0
    }
}