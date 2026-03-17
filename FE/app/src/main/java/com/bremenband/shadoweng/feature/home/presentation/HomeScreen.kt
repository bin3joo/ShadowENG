package com.bremenband.shadoweng.feature.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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


@Composable
fun HomeScreen(
    onNavigateToStudy: (sessionId: Long) -> Unit,
    onNavigateToGame: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToMoreSessions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()

) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.navigateToStudy.collect { onNavigateToStudy(it) } }
    LaunchedEffect(Unit) { viewModel.navigateToGame.collect { onNavigateToGame() } }
    LaunchedEffect(Unit) { viewModel.navigateToRegister.collect { onNavigateToRegister() } }
    LaunchedEffect(Unit) { viewModel.navigateToMoreSessions.collect { onNavigateToMoreSessions() } }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 매일 따라 말해요
        uiState.streak?.let { streak ->
            StreakSection(
                streak = streak,
                onMoreClick = { viewModel.onEvent(HomeEvent.ClickMoreSessions) }
            )
        }

        // 이어서 말하기
        uiState.latestSession?.let { session ->
            LatestSessionSection(
                session = session,
                onSessionClick = { viewModel.onEvent(HomeEvent.ClickLatestSession) },
                onMoreClick = { viewModel.onEvent(HomeEvent.ClickMoreSessions) }
            )
        }

        // 앵무 구하기
        GameSection(onClick = { viewModel.onEvent(HomeEvent.ClickGame) })

        // 영상 등록 CTA
        Button(
            onClick = { viewModel.onEvent(HomeEvent.ClickRegister) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500))
        ) {
            Text("영상 등록하고 학습 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        }
    }
}

@Composable
private fun StreakSection(streak: StreakData, onMoreClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("매일 따라 말해요!", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("더보기", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.clickable { onMoreClick() })
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🔥", fontSize = 40.sp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${streak.currentDay}일째 말하는 중", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        streak.weeklyStatus.forEach { done ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (done) Color(0xFFE53935) else Color(0xFFE8E8E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun LatestSessionSection(
    session: LatestSession,
    onSessionClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("이어서 말하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("더보기", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.clickable { onMoreClick() })
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onSessionClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column {
                // 썸네일
                Image(
                    painter = painterResource(id = R.drawable.thumbnail),
                    contentDescription = "썸네일",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("진행률 ${session.progressRate}%", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun GameSection(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("앵무 구하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("지금까지 획득한 메달", fontSize = 15.sp, color = Color.Gray)
                // TODO: 메달 UI 추가
            }
        }
    }
}