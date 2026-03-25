package com.bremenband.shadoweng.feature.study.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.feature.study.domain.DifficultSentence

@Composable
fun StudyReportScreen(
    sessionId: Long,
    reportId: Long,
    onBackToSession: () -> Unit,
    viewModel: StudyReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(reportId) { viewModel.loadReport(sessionId, reportId) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE53935))
        }
        return
    }

    val report = uiState.report ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("세션 돌아보기", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("종합 점수", fontSize = 14.sp, color = Color(0xFF888888))
                Text(
                    "${report.totalScore}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935)
                )
                Text(
                    "속도 ${report.speedScore}점 | 강세 ${report.stressScore}점 | 억양 ${report.intonationScore}점",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Row {
                    Text("${report.sentenceCount}문장", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    Text("을 말했어요!", fontSize = 14.sp, color = Color(0xFF444444))
                }
            }
        }

        if (report.difficultSentences.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("가장 어려웠던 문장 TOP3", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        report.difficultSentences.take(3).forEachIndexed { index, sentence ->
                            DifficultSentenceItem(sentence = sentence)
                            if (index < minOf(report.difficultSentences.size, 3) - 1) {
                                HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "따라 말할수록 말하기는 자연스러워져요!",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onBackToSession,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
        ) {
            Text("복습하러 가기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
        }

        OutlinedButton(
            onClick = onBackToSession,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF362000))
        ) {
            Text("홈으로 돌아가기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DifficultSentenceItem(sentence: DifficultSentence) {
    var bookmarked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = sentence.content,
                fontSize = 14.sp,
                color = Color(0xFF1A1A1A),
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f)
            )
            if (sentence.averageScore > 0) {
                Text(
                    "%.0f점".format(sentence.averageScore),
                    fontSize = 12.sp,
                    color = Color(0xFFFF5D5D),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sentence.feedback, fontSize = 13.sp, color = Color(0xFF888888), modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "북마크",
                tint = if (bookmarked) Color(0xFFE53935) else Color(0xFFBBBBBB),
                modifier = Modifier.size(20.dp).clickable { bookmarked = !bookmarked }
            )
        }
    }
}