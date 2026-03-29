package com.bremenband.shadoweng.feature.content.presentation.range

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.ThumbnailImage
import kotlin.math.roundToInt

@Composable
fun ContentRangeScreen(
    embedUrl: String,
    onNavigateToLoading: (Triple<String, Double, Double>) -> Unit,
    viewModel: ContentRangeViewModel = hiltViewModel()
) {
    LaunchedEffect(embedUrl) { viewModel.init(embedUrl) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToLoading.collect { onNavigateToLoading(it) }
    }

    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }

    val startSec = parseTimeInput(startInput)
    val endSec = parseTimeInput(endInput)
    val videoDuration = uiState.videoDuration.toInt()

    val rangeDuration = if (startSec != null && endSec != null) endSec - startSec else null
    val isRangeExceeded = rangeDuration != null && rangeDuration > 60
    val isOutOfBounds = startSec != null && endSec != null &&
            (startSec < 0 || endSec > videoDuration)
    val isValid = startSec != null && endSec != null &&
            endSec > startSec && !isRangeExceeded && !isOutOfBounds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            "학습할 구간을 선택해주세요",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF362000),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "시작/끝 시간을 입력해요\n최대 1분까지 선택 가능해요",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        ThumbnailImage(
            url = uiState.thumbnailUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 영상 길이 박스 (TimeInputField 위)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
//                        .background(Color(0xFFF5F5F3))
                        .padding(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🎬 영상 길이: ${formatSeconds(videoDuration)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF362000)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeInputField(
                        label = "시작",
                        value = startInput,
                        onValueChange = { startInput = it },
                        isError = startInput.isNotEmpty() && startSec == null,
                        modifier = Modifier.weight(1f)
                    )
                    TimeInputField(
                        label = "종료",
                        value = endInput,
                        onValueChange = { endInput = it },
                        isError = endInput.isNotEmpty() && endSec == null,
                        modifier = Modifier.weight(1f)
                    )
                }

// 상태 메시지
                val statusMessage = when {
                    startInput.isEmpty() || endInput.isEmpty() -> null
                    startSec == null || endSec == null -> "⚠️ 0:00 형식으로 입력해주세요"
                    endSec <= startSec -> "⚠️ 종료 시간이 시작 시간보다 커야 해요"
                    isOutOfBounds -> "⚠️ 영상 범위를 벗어났어요 (0:00 ~ ${formatSeconds(videoDuration)})"
                    isRangeExceeded -> "⚠️ 최대 1분까지 선택 가능해요"
                    else -> "⏱ 선택 구간: ${formatSeconds(rangeDuration!!)}"
                }

                if (statusMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isValid) Color(0xFFF5F5F3) else Color(0xFFFFF0F0))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            statusMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isValid) Color(0xFF362000) else Color(0xFFFF5D5D)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (startSec != null && endSec != null) {
                    viewModel.submitRange(startSec.toDouble(), endSec.toDouble())
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEDF57),
                disabledContainerColor = Color(0xFFE8E8E8)
            )
        ) {
            Text(
                "이 구간으로 학습 시작하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isValid) Color(0xFF362000) else Color(0xFFAAAAAA)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TimeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("0:00", fontSize = 14.sp, color = Color(0xFFCCCCCC)) },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFEDF57),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                errorBorderColor = Color(0xFFFF5D5D)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// parseTimeInput - 콜론 형식만 허용
private fun parseTimeInput(input: String): Int? {
    if (input.isBlank()) return null
    if (!input.contains(":")) return null
    val parts = input.split(":")
    if (parts.size != 2) return null
    val m = parts[0].toIntOrNull() ?: return null
    val s = parts[1].toIntOrNull() ?: return null
    if (s >= 60) return null
    return m * 60 + s
}

private fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}