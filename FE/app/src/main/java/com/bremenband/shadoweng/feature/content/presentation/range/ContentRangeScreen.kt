package com.bremenband.shadoweng.feature.content.presentation.range

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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

    val rangeStart = uiState.sliderStart
    val rangeEnd = uiState.sliderEnd
    val videoDuration = uiState.videoDuration
    val sliderRange = rangeStart..rangeEnd

    val rangeDuration = (rangeEnd - rangeStart).roundToInt()
    val isRangeExceeded = rangeDuration > 60
    val isValid = rangeEnd > rangeStart && !isRangeExceeded

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
            "슬라이더로 시작/끝 구간을 조절해요\n최대 1분까지 선택 가능해요",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 썸네일
        AsyncImage(
            model = uiState.thumbnailUrl.ifEmpty { null },
            contentDescription = "썸네일",
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.thumbnail),
            error = painterResource(id = R.drawable.thumbnail),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 선택 구간 정보 카드
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
                // 시작/종료 시간 표시
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeChip(label = "시작", time = formatSeconds(rangeStart.roundToInt()))
                    TimeChip(label = "종료", time = formatSeconds(rangeEnd.roundToInt()))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RangeSlider(
                        value = sliderRange,
                        onValueChange = { range ->
                            if (range.endInclusive - range.start >= 1f) {
                                viewModel.onEvent(ContentRangeEvent.SliderStartChanged(range.start))
                                viewModel.onEvent(ContentRangeEvent.SliderEndChanged(range.endInclusive))
                            }
                        },
                        valueRange = 0f..videoDuration,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF362000),
                            activeTrackColor = Color(0xFFFEDF57),
                            inactiveTrackColor = Color(0xFFEEEEEE)
                        )
                    )
                }

                // 선택 시간 표시
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRangeExceeded) Color(0xFFFFF0F0) else Color(0xFFF5F5F3)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "선택 구간",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Text(
                        if (isRangeExceeded) "⚠️ 최대 1분 초과"
                        else "⏱ ${formatSeconds(rangeDuration)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRangeExceeded) Color(0xFFFF5D5D) else Color(0xFF362000)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.onEvent(ContentRangeEvent.Submit) },
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
private fun TimeChip(label: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color(0xFF888888))
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEDF57).copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Text(
                time,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF362000),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RangeBar(start: Float, end: Float, max: Float) {
    val startRatio = (start / max).coerceIn(0f, 1f)
    val endRatio = (end / max).coerceIn(0f, 1f)

    val animStart by animateFloatAsState(targetValue = startRatio, label = "startAnim")
    val animEnd by animateFloatAsState(targetValue = endRatio, label = "endAnim")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFEEEEEE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFE082))
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animEnd - animStart)
                .offset(x = (animStart * 1000).dp.coerceAtLeast(0.dp))  // 근사치
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFEDF57))
        )
    }
}

private fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}