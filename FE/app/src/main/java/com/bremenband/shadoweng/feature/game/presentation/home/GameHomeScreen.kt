package com.bremenband.shadoweng.feature.game.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.bremenband.shadoweng.R

@Composable
fun GameHomeScreen(
    onNavigateToPlay: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GameHomeViewModel = hiltViewModel()
) {

    DisposableEffect(Unit) {
        viewModel.startBgm()
        onDispose { }
    }

    LaunchedEffect(Unit) { viewModel.navigateToPlay.collect { onNavigateToPlay() } }
    LaunchedEffect(Unit) { viewModel.navigateToLeaderboard.collect { onNavigateToLeaderboard() } }

    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 버튼 외 전체 화면 클릭 → 게임 시작
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { viewModel.onEvent(GameHomeEvent.ClickPlay) }
    ) {
        Image(
            painter = painterResource(id = R.drawable.game_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        val context = LocalContext.current
        val gifLoader = remember {
            ImageLoader.Builder(context)
                .components { add(GifDecoder.Factory()) }
                .build()
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.raw.engmu_fly)
                .build(),
            imageLoader = gifLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),          // 크기 조정
            contentScale = ContentScale.Fit
        )

        // X 버튼 (왼쪽 상단)
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
                imageVector = Icons.Default.Close,
                contentDescription = "나가기",
                tint = Color(0xFF362000),
                modifier = Modifier.size(24.dp)
            )
        }

        // 리더보드 버튼 (오른쪽 상단)
        IconButton(
            onClick = { viewModel.onEvent(GameHomeEvent.ClickLeaderboard) },
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

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Image(
                painter = painterResource(id = R.drawable.game_logo),
                contentDescription = "로고",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "아무 곳이나 터치해 게임을 시작하세요 !",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF362000)
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}