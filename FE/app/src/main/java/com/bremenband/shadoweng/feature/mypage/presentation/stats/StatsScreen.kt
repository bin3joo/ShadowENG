package com.bremenband.shadoweng.feature.mypage.presentation.stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog

@Composable
fun StatsScreen(
    onNavigateToReport: (sessionId: Long, reportId: Long) -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showReportPager by remember { mutableStateOf(false) }

    if (showReportPager) {
        ReportPagerDialog(
            reports = state.reports,
            onDismiss = { showReportPager = false },
            onNavigateToReport = onNavigateToReport
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F3)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                "학습 통계",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(modifier = Modifier.weight(1f), icon = "💬", label = "따라 말한 문장", value = "${state.totalSentences}", unit = "문장")
                StatsCard(modifier = Modifier.weight(1f), icon = "🕐", label = "공부한 시간", value = "${state.totalMinutes}", unit = "분")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("리포트 모아보기", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                if (state.reports.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFBBBBBB),
                        modifier = Modifier.size(20.dp).clickable { showReportPager = true }
                    )
                }
            }
        }

        items(state.reports) { report ->
            ReportCard(
                report = report,
                onClick = { onNavigateToReport(report.sessionId, report.reportId) }
            )
        }
    }
}

@Composable
private fun StatsCard(modifier: Modifier = Modifier, icon: String, label: String, value: String, unit: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(icon, fontSize = 28.sp)
            Text(label, fontSize = 12.sp, color = Color(0xFF888888))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D))
                Text(" $unit", fontSize = 13.sp, color = Color(0xFF362000))
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
            Image(
                painter = painterResource(R.drawable.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
    val displayReports = (reports + listOf(
        ReportSummaryUi(3L, 3L, "The Power of Vulnerability", "", 85, 90, "2026.03.17"),
        ReportSummaryUi(4L, 4L, "How Great Leaders Inspire Action", "", 55, 68, "2026.03.15"),
        ReportSummaryUi(5L, 5L, "Your Body Language May Shape Who You Are", "", 91, 88, "2026.03.12"),
    )).take(5)

    val pagerState = rememberPagerState(pageCount = { displayReports.size })

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
                    Text(displayReports[pagerState.currentPage].date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = Color(0xFF888888))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(displayReports.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) Color(0xFF362000) else Color(0xFFCCCCCC))
                        )
                    }
                }

                HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { page ->
                    val report = displayReports[page]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToReport(report.sessionId, report.reportId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(painter = painterResource(R.drawable.thumbnail), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(report.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000), maxLines = 2)
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F3)), elevation = CardDefaults.cardElevation(0.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("종합 점수", fontSize = 12.sp, color = Color(0xFF888888))
                                        Text("${(report.studyScore + report.reviewScore) / 2}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF5D5D))
                                        Text("학습 ${report.studyScore}점 | 복습 ${report.reviewScore}점", fontSize = 12.sp, color = Color(0xFF888888))
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