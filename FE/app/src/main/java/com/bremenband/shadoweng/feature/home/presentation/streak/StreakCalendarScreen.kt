package com.bremenband.shadoweng.feature.home.presentation.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Image
import com.bremenband.shadoweng.R
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StreakCalendarScreen(
    onNavigateBack: () -> Unit,
    viewModel: StreakCalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFEDF57))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.Default.ChevronLeft,
                    tint = Color(0xFF362000),
                    contentDescription = "뒤로가기")
            }
            Text(
                "학습 현황",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Text("🔥", fontSize = 28.sp) },
                    label = "최장 연속 출석",
                    value = state.longestStreak
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Text("📅", fontSize = 28.sp) },
                    label = "총 출석일",
                    value = state.totalVisitedDays
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${state.currentMonth.year}",
                        fontSize = 12.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.prevMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                        }
                        Text(
                            "${state.currentMonth.monthValue}월",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF362000)
                        )
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = Color(0xFF888888),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    CalendarGrid(
                        currentMonth = state.currentMonth,
                        studyDates = state.studyDates
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: Int
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon()
            Text(label, fontSize = 12.sp, color = Color(0xFF888888))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$value",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF5D5D)
                )
                Text(" 일", fontSize = 14.sp, color = Color(0xFF362000))
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: LocalDate,
    studyDates: Set<LocalDate>
) {
    val yearMonth = YearMonth.of(currentMonth.year, currentMonth.month)
    val firstDayOfWeek = currentMonth.dayOfWeek.value % 7  // 일=0, 월=1 ... 토=6
    val daysInMonth = yearMonth.lengthOfMonth()
    val prevYearMonth = yearMonth.minusMonths(1)
    val nextYearMonth = yearMonth.plusMonths(1)

    val cells = mutableListOf<Pair<LocalDate, Boolean>>() // date, isCurrentMonth

    repeat(firstDayOfWeek) { i ->
        val day = prevYearMonth.lengthOfMonth() - firstDayOfWeek + i + 1
        cells.add(LocalDate.of(prevYearMonth.year, prevYearMonth.month, day) to false)
    }
    repeat(daysInMonth) { day ->
        cells.add(LocalDate.of(currentMonth.year, currentMonth.month, day + 1) to true)
    }
    val remaining = (7 - cells.size % 7) % 7
    repeat(remaining) { i ->
        cells.add(LocalDate.of(nextYearMonth.year, nextYearMonth.month, i + 1) to false)
    }

    cells.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { (date, isCurrentMonth) ->
                val isStudied = isCurrentMonth && studyDates.contains(date)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStudied) {
                        Image(
                            painter = painterResource(R.drawable.engmu_stamp),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.85f)
                        )
                    } else {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 13.sp,
                            color = if (isCurrentMonth) Color(0xFF362000) else Color(0xFFCCCCCC)
                        )
                    }
                }
            }
        }
    }
}