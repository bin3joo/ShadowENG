package com.bremenband.shadoweng.feature.game.presentation.play

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bremenband.shadoweng.R
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun RoundResultModal(
    round: Int,
    score: Double,
    heartGained: Boolean,
    totalHearts: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400), repeatMode = RepeatMode.Reverse
        ), label = "heartScale"
    )

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Round 완료 텍스트
                Text(
                    "Round $round 완료!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF362000)
                )

                // 점수
                Text(
                    "%.0f점".format(score),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFEDF57)
                )

                // 현재 하트 상태 부분 교체
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isGained = index < totalHearts
                        val isJustGained = heartGained && index == totalHearts - 1  // 방금 획득한 하트

                        Image(
                            painter = painterResource(R.drawable.icon_heart),
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (isJustGained) 40.dp else 28.dp)  // 방금 획득한 것만 크게
                                .scale(if (isJustGained) heartScale else 1f)  // 방금 획득한 것만 pulse
                                .graphicsLayer { alpha = if (isGained) 1f else 0.25f }
                        )
                    }
                }

                // 다음 라운드 안내
                if (round < 3) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            //.background(Color(0xFFF5F5F3))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Round ${round + 1} 준비 중...",
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        }
    }
}