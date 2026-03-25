package com.bremenband.shadoweng.feature.game.presentation.play

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bremenband.shadoweng.R

@Composable
fun VoiceNotRecognizedModal(
    onRetry: () -> Unit
) {
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
                Image(
                    painter = painterResource(R.drawable.engmu_question),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )

                Text(
                    "음성이 인식되지 않았어요",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF362000)
                )

                Text(
                    "조금 더 크고 명확하게\n다시 말해볼까요?",
                    fontSize = 14.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
                ) {
                    Text(
                        "다시 녹음하기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF362000)
                    )
                }
            }
        }
    }
}