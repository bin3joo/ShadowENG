package com.bremenband.shadoweng.feature.game.presentation.play

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bremenband.shadoweng.R
import com.bremenband.shadoweng.core.ui.component.AudioPlayButton
import com.bremenband.shadoweng.core.ui.component.AudioRecordButton
import com.bremenband.shadoweng.core.ui.component.ExitConfirmDialog
import com.bremenband.shadoweng.feature.game.domain.model.GameFinalResult


@Composable
fun GamePlayScreen(
    onNavigateToResult: (GameFinalResult, Int, Double) -> Unit,  // Double 추가
    onNavigateToLeaderboard: () -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: GamePlayViewModel = hiltViewModel()
) {

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    LaunchedEffect(state.referenceAudioUrl) {
        if (state.referenceAudioUrl.isNotEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(state.referenceAudioUrl))
            exoPlayer.prepare()
            if (!state.isRecording) exoPlayer.play()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GamePlayEffect.NavigateToResult ->
                    onNavigateToResult(effect.finalResult, effect.hearts, effect.prevBest)  // prevBest 추가
                is GamePlayEffect.NavigateToLeaderboard ->
                    onNavigateToLeaderboard()
                is GamePlayEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message)
                is GamePlayEffect.ShowVoiceNotRecognized -> {}
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onIntent(GamePlayIntent.SetAudioPlaying(isPlaying))
            }
        }
        exoPlayer.addListener(listener)
    }

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        ExitConfirmDialog(
            message = "지금 게임을 중단하면 기록이 저장되지 않아요.\n정말 중단하시겠어요?",
            dismissLabel = "이어서 하기",
            onConfirm = { onNavigateBack() },
            onDismiss = { showExitDialog = false }
        )
    }

    if (state.showRoundModal) {
        RoundResultModal(
            round = state.round,
            score = state.lastRoundScore,
            heartGained = state.lastRoundHeartGained,
            totalHearts = state.hearts
        )
    }

    if (state.showVoiceNotRecognizedModal) {
        VoiceNotRecognizedModal(
            onRetry = { viewModel.onIntent(GamePlayIntent.DismissVoiceModal) }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF362000),
                    contentColor = Color.White
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // 배경
            Image(
                painter = painterResource(
                    id = when (state.engmuMood) {
                        EngmuMood.NEUTRAL -> R.drawable.background1
                        EngmuMood.INTERESTED -> R.drawable.background2
                        EngmuMood.EXCITED -> R.drawable.background3
                        EngmuMood.LOVE -> R.drawable.background4
                    }
                ),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // X 버튼
            IconButton(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 24.dp, start = 16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "나가기",
                    tint = Color(0xFF362000),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 하트 + Round 표시 (고정)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        repeat(3) { index ->
                            Image(
                                painter = painterResource(id = R.drawable.icon_heart),
                                contentDescription = "하트",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(horizontal = 4.dp)
                                    .graphicsLayer { alpha = if (index < state.hearts) 1f else 0.25f }
                            )
                        }
                    }
                    Text(
                        "Round ${state.round}/3",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF525252)
                    )
                }

                // 말풍선 (고정)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        AnimatedContent(
                            targetState = state.engmuMood,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "moodText"
                        ) { mood ->
                            Text(
                                text = when (mood) {
                                    EngmuMood.NEUTRAL -> "아직 잉무가 당신을 낯설어해요..."
                                    EngmuMood.INTERESTED -> "잉무가 관심을 보이기 시작했어요!"
                                    EngmuMood.EXCITED -> "분위기가 좋은데요? 조금만 더 꼬셔볼까요?"
                                    EngmuMood.LOVE -> "꼬시기 대성공! 잉무가 완전히 반했어요!"
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontSize = 14.sp,
                                color = Color(0xFF362000),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 잉무 이미지 — 남은 공간 채움 (화면 상대적)
                Image(
                    painter = painterResource(id = R.drawable.engmu),
                    contentDescription = "잉무",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentScale = ContentScale.Fit
                )

                // 하단 카드 — 컨텐츠 크기만큼만 (고정)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 라운드 안내 텍스트 (고정)
                        Text(
                            text = when (state.round) {
                                1 -> "자막과 함께 따라 말해보세요."
                                2 -> "자막을 조금 지우고 따라 말해 볼까요?"
                                else -> "자막 없이 따라 말해보세요!"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )

                        // 문장 표시 영역 (고정 높이)
                        val displayText = when (state.round) {
                            1 -> state.sentence
                            2 -> state.maskedSentence
                            else -> null
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),  // 최소 높이 고정 → 텍스트 없어도 레이아웃 안흔들림
                            contentAlignment = Alignment.Center
                        ) {
                            if (!displayText.isNullOrEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AudioPlayButton(
                                        isPlaying = state.isPlayingAudio,
                                        onClick = {
                                            if (!state.isRecording) {
                                                exoPlayer.seekTo(0)
                                                exoPlayer.play()
                                            }
                                        },
                                        size = 36.dp
                                    )
                                    Text(
                                        displayText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF362000),
                                        lineHeight = 26.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                AudioPlayButton(
                                    isPlaying = state.isPlayingAudio,
                                    onClick = {
                                        if (!state.isRecording) {
                                            exoPlayer.seekTo(0)
                                            exoPlayer.play()
                                        }
                                    },
                                    size = 48.dp
                                )
                            }
                        }

                        // 녹음 버튼 / 분석 중 (고정 높이 영역)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),  // 고정 — 분석 중/녹음 전환 시 카드 크기 안변함
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = state.isAnalyzing,
                                transitionSpec = {
                                    fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                                },
                                label = "analyzeTransition"
                            ) { isAnalyzing ->
                                if (isAnalyzing) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFF5D5D),
                                            modifier = Modifier.size(52.dp),
                                            strokeWidth = 5.dp
                                        )
                                        Text(
                                            "발음 분석 중...",
                                            fontSize = 13.sp,
                                            color = Color(0xFF888888)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AudioRecordButton(
                                            isRecording = state.isRecording,
                                            enabled = !state.isPlayingAudio,
                                            onStartCountdown = { viewModel.onIntent(GamePlayIntent.StartCountdown) },
                                            onStopRecording = { viewModel.onIntent(GamePlayIntent.StopRecording) }
                                        )
                                        Text(
                                            when {
                                                state.isRecording -> "녹음 중... 버튼을 눌러 완료하세요!"
                                                state.isPlayingAudio -> "오디오 재생 중..."
                                                else -> "버튼을 눌러 녹음을 시작하세요!"
                                            },
                                            fontSize = 12.sp,
                                            color = Color(0xFF888888)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}