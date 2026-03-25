package com.bremenband.shadoweng.feature.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
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
import coil.compose.AsyncImage
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.AnimatedEngmuFire
import com.bremenband.shadoweng.core.ui.util.tierToDrawable

@Composable
fun HomeScreen(
    onNavigateToStudy: (sessionId: Long) -> Unit,
    onNavigateToGame: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToMoreSessions: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.navigateToStudy.collect { onNavigateToStudy(it) } }
    LaunchedEffect(Unit) { viewModel.navigateToGame.collect { onNavigateToGame() } }
    LaunchedEffect(Unit) { viewModel.navigateToRegister.collect { onNavigateToRegister() } }
    LaunchedEffect(Unit) { viewModel.navigateToMoreSessions.collect { onNavigateToMoreSessions() } }
    LaunchedEffect(Unit) { viewModel.navigateToStreak.collect { onNavigateToCalendar() } }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFEDF57))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        uiState.streak?.let { streak ->
            StreakSection(
                streak = streak,
                onNavigateToCalendar = { viewModel.onEvent(HomeEvent.ClickStreak) }
            )
        }

        Button(
            onClick = { viewModel.onEvent(HomeEvent.ClickRegister) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
        ) {
            Text("좋아하는 영상으로 학습 시작하기", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
        }

        Spacer(modifier = Modifier.height(4.dp))

        uiState.latestSession?.let { session ->
            LatestSessionSection(
                session = session,
                onSessionClick = { viewModel.onEvent(HomeEvent.ClickLatestSession) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

//         TODO : 확인 후 삭제 처리
//        GameSection(
//            onClick = { viewModel.onEvent(HomeEvent.ClickGame) },
//            leagueTopScore = uiState.gameSectionData?.leagueTopScore ?: 0,
//            myScore = uiState.gameSectionData?.myScore ?: 0,
//            tier = uiState.gameSectionData?.tier ?: ""
//        )
    }
}

@Composable
private fun StreakSection(
    streak: StreakData,
    onNavigateToCalendar: () -> Unit
) {
    val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "매일 따라 말해요",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF362000)
        )
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
                AnimatedEngmuFire(modifier = Modifier.size(48.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${streak.currentDay}일째 말하는 중",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "달력 보기",
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onNavigateToCalendar() }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        streak.weeklyStatus.forEachIndexed { index, done ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (done) Color(0xFFFF5D5D) else Color(0xFFE8E8E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = listOf("일","월","화","수","목","금","토").getOrElse(index) { "" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
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
    onSessionClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("이어서 말하기", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onSessionClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column {
                AsyncImage(
                    model = session.thumbnailUrl.ifEmpty { null },
                    contentDescription = "썸네일",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.thumbnail),
                    error = painterResource(id = R.drawable.thumbnail),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title.ifEmpty { "학습 세션" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF362000),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${session.completedSentences} / ${session.totalSentences} 문장",
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (session.totalSentences > 0)
                                    session.completedSentences / session.totalSentences.toFloat()
                                else 0f
                            },
                            modifier = Modifier
                                .width(100.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFFEDF57),
                            trackColor = Color(0xFFE0E0E0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSection(
    onClick: () -> Unit,
    leagueTopScore: Int = 0,
    myScore: Int = 0,
    tier: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("잉무를 꼬셔라", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Image(
                    painter = painterResource(R.drawable.background3),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(tierToDrawable(tier)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    )

                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("리그 최고 기록", fontSize = 11.sp, color = Color(0xFF888888))
                                    Text(
                                        "$leagueTopScore",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFEDF57)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(48.dp)
                                        .align(Alignment.CenterVertically)
                                        .background(Color(0xFFE0E0E0))
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("내 기록", fontSize = 11.sp, color = Color(0xFF888888))
                                    Text(
                                        "$myScore",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFF5D5D)
                                    )
                                }
                            }
                        }

                        Text("최고 리그에 도전하세요!", fontSize = 12.sp, color = Color(0xFF362000))

                        Button(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                        ) {
                            Text("잉무 꼬시러 가기", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
                        }
                    }
                }
            }        }
    }
}