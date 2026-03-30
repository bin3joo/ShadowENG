package com.bremenband.shadoweng.feature.game.presentation.levelselect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.feature.game.domain.model.LevelStatus

@Composable
fun GameLevelSelectScreen(
    onNavigateToPlay: (level: Int, prevBest: Double) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {},
    viewModel: GameLevelSelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToPlay.collect { level ->
            val prevBest = uiState.levels.find { it.level == level }?.todayBestScore ?: 0.0
            onNavigateToPlay(level, prevBest)
        }
    }

    // 화면 진입 시마다 레벨 데이터 갱신
    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    DisposableEffect(Unit) {
        if (!viewModel.isBgmPlaying()) viewModel.startBgm()
        onDispose { }  // stopBgm은 SelectLevel 이벤트에서 처리
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.game_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
            // .background(Color(0xFFFEDF57))
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBackIosNew,
                contentDescription = "뒤로",
                tint = Color(0xFF362000),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = onNavigateToLeaderboard,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.leaderboard),
                contentDescription = "리더보드",
                modifier = Modifier.size(32.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 게임 규칙 토글
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onEvent(GameLevelSelectEvent.ToggleRules) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                            Text("게임 규칙", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
                        }
                        Icon(
                            if (uiState.isRulesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color(0xFF888888)
                        )
                    }

                    if (uiState.isRulesExpanded) {
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F3), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                buildAnnotatedString {
                                    append("앵무의 마음을 사로잡아 ❤️ 를 모아보세요!")
                                },
                                fontSize = 14.sp, color = Color(0xFF362000), fontWeight = FontWeight.Bold
                            )
                            listOf(
                                Triple("한 문장마다 ", "3번", "의 말하기 기회가 주어져요."),
                                Triple("말하기에 성공하면 ", "하트 1개", " 획득!"),
                                Triple("도전이 진행될수록 자막이 점점 줄어들어요.", null, null),
                                Triple("모은 하트 수에 따라 ", "추가 점수", " 획득!"),
                                Triple("하트를 하나도 얻지 못해도 재도전 가능!", null, null),
                            ).forEach { (before, highlight, after) ->
                                Row(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("• ", fontSize = 14.sp, color = Color(0xFF555555))
                                    if (highlight != null) {
                                        Text(
                                            buildAnnotatedString {
                                                append(before)
                                                withStyle(
                                                    androidx.compose.ui.text.SpanStyle(
                                                        color = Color(0xFFFF5D5D),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                ) { append(highlight) }
                                                if (after != null) append(after)
                                            },
                                            fontSize = 14.sp, color = Color(0xFF555555), lineHeight = 22.sp
                                        )
                                    } else {
                                        Text(before, fontSize = 14.sp, color = Color(0xFF555555), lineHeight = 22.sp)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                    }

                    Text(
                        "레벨을 선택하고 게임을 시작하세요!",
                        fontSize = 14.sp, color = Color(0xFF555555),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF5D5D),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (uiState.levels.isNotEmpty()) {
                                uiState.levels.forEach { levelStatus ->
                                    LevelButton(
                                        levelStatus = levelStatus,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (levelStatus.unlocked)
                                                viewModel.onEvent(GameLevelSelectEvent.SelectLevel(levelStatus.level))
                                        }
                                    )
                                }
                            } else {
                                listOf(1, 2, 3).forEach { lv ->
                                    LevelButton(
                                        levelStatus = LevelStatus(lv, lv == 1, null, null),
                                        modifier = Modifier.weight(1f),
                                        onClick = { if (lv == 1) viewModel.onEvent(GameLevelSelectEvent.SelectLevel(lv)) }
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
private fun LevelButton(
    levelStatus: LevelStatus,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isLocked = !levelStatus.unlocked
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLocked) Color(0xFFD0D0D0) else Color(0xFFFEDF57))
            .clickable(enabled = !isLocked) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (isLocked) "Lv.${levelStatus.level} 🔒" else "Lv.${levelStatus.level}",
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isLocked) Color(0xFF888888) else Color(0xFF362000)
            )
            if (levelStatus.todayBestScore != null) {
                Text("%.0f점".format(levelStatus.todayBestScore), fontSize = 13.sp, color = Color(0xFF362000))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(levelStatus.todayBestHearts ?: 0) { Text("❤️", fontSize = 12.sp) }
                }
            } else if (!isLocked) {
                Text("기록 없음", fontSize = 13.sp, color = Color(0xFF888888))
            }
        }
    }
}