package com.bremenband.shadoweng.feature.stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import com.bremenband.shadoweng.core.ui.component.ThumbnailImage
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen(
    onNavigateToReport: (sessionId: Long, reportId: Long) -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    selectedDate?.let { date ->
        val reports = state.reportDates[date] ?: emptyList()
        if (reports.isNotEmpty()) {
            DailyReportDialog(
                date = date,
                reports = reports,  // 추가
                onDismiss = { selectedDate = null },
                onNavigateToReport = onNavigateToReport
            )
        } else {
            selectedDate = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F3)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                "학습 통계",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${state.currentMonth.year}", fontSize = 12.sp, color = Color(0xFF888888),
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.prevMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                        }
                        Text("${state.currentMonth.monthValue}월", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
                        }
                    }
                    CalendarGrid(
                        currentMonth = state.currentMonth,
                        studyDates = state.studyDates,
                        reportDates = state.reportDates,  // 추가
                        onDateClick = { selectedDate = it }
                    )
                    Text(
                        "도장을 탭하면 그날의 리포트를 볼 수 있어요",
                        fontSize = 11.sp, color = Color(0xFFAAAAAA),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), { Text("🔥", fontSize = 28.sp) }, "최장 연속 출석", "${state.longestStreak}", "일")
                StatCard(Modifier.weight(1f), { Text("📅", fontSize = 28.sp) }, "총 출석일", "${state.totalVisitedDays}", "일")
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), { Text("💬", fontSize = 28.sp) }, "따라 말한 문장", "${state.totalSentences}", "문장")
                StatCard(Modifier.weight(1f), { Text("🕐", fontSize = 28.sp) }, "공부한 시간", "${state.totalMinutes}", "분")
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, fontSize = 12.sp, color = Color(0xFF888888))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D))
                Text(" $unit", fontSize = 13.sp, color = Color(0xFF362000))
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: LocalDate,
    studyDates: Set<LocalDate>,
    reportDates: Map<LocalDate, List<ReportSummaryUi>>,  // 추가
    onDateClick: (LocalDate) -> Unit
) {
    val yearMonth = YearMonth.of(currentMonth.year, currentMonth.month)
    val firstDayOfWeek = currentMonth.dayOfWeek.value % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val prevYearMonth = yearMonth.minusMonths(1)
    val nextYearMonth = yearMonth.plusMonths(1)

    val cells = mutableListOf<Pair<LocalDate, Boolean>>()
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
                val hasReport = isCurrentMonth && reportDates.containsKey(date)  // studyDates 대신 reportDates 기준
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .then(if (hasReport) Modifier.clickable { onDateClick(date) } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasReport) {
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

@Composable
private fun ReportCard(report: ReportSummaryUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            ThumbnailImage(
                url = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(report.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                Text("학습 ${report.studyScore}점 | 복습 ${report.reviewScore}점", fontSize = 13.sp, color = Color(0xFF888888))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportPagerDialog(
    reports: List<ReportSummaryUi>,
    onDismiss: () -> Unit,
    onNavigateToReport: (sessionId: Long, reportId: Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { reports.size })

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(reports[pagerState.currentPage].date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = Color(0xFF888888))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(reports.size) { index ->
                        Box(
                            modifier = Modifier.padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) Color(0xFF362000) else Color(0xFFCCCCCC))
                        )
                    }
                }
                HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { page ->
                    val report = reports[page]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToReport(report.sessionId, report.reportId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column {
                            ThumbnailImage(
                                url = report.thumbnailUrl,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            )
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(report.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000), maxLines = 2)
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("종합 점수", fontSize = 12.sp, color = Color(0xFF888888))
                                        Text(
                                            "${report.studyScore}",
                                            fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onNavigateToReport(report.sessionId, report.reportId) },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                                ) {
                                    Text("자세히 보기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyReportDialog(
    date: LocalDate,
    reports: List<ReportSummaryUi>,  // 추가, mockReports 제거
    onDismiss: () -> Unit,
    onNavigateToReport: (sessionId: Long, reportId: Long) -> Unit
) {
    // mockReports 제거하고 reports 파라미터 사용
    val pagerState = rememberPagerState(pageCount = { reports.size })

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${date.monthValue}월 ${date.dayOfMonth}일 리포트",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = Color(0xFF888888))
                    }
                }

                if (reports.size > 1) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(reports.size) { index ->
                            Box(
                                modifier = Modifier.padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (pagerState.currentPage == index) Color(0xFF362000) else Color(0xFFCCCCCC))
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 12.dp
                ) { page ->
                    val report = reports[page]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToReport(report.sessionId, report.reportId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column {
                            ThumbnailImage(
                                url = report.thumbnailUrl,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            )
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(report.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000), maxLines = 2)
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("종합 점수", fontSize = 12.sp, color = Color(0xFF888888))
                                        Text(
                                            "${report.studyScore}",
                                            fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onNavigateToReport(report.sessionId, report.reportId) },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                                ) {
                                    Text("자세히 보기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}