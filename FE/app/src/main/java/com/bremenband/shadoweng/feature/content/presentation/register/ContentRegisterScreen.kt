package com.bremenband.shadoweng.feature.content.presentation.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
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
import com.bremenband.shadoweng.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun ContentRegisterScreen(
    onNavigateToRange: (videoId: String) -> Unit,
    viewModel: ContentRegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToRange.collect { onNavigateToRange(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F3))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 잉무 이미지
        Image(
            painter = painterResource(R.drawable.engmu),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "좋아하는 영상으로\n영어를 말해봐요!",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF362000),
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "YouTube 링크를 붙여넣으면\n잉무가 학습 자료로 만들어줘요",
            fontSize = 14.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = Color(0xFFFEDF57),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "YouTube 링크",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF362000)
                    )
                }
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = { viewModel.onEvent(ContentRegisterEvent.UrlChanged(it)) },
                    placeholder = { Text("https://youtube.com/...", color = Color(0xFFCCCCCC), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.url.isNotEmpty() && !uiState.isValidUrl,
                    supportingText = {
                        if (uiState.url.isNotEmpty() && !uiState.isValidUrl) {
                            Text("올바른 YouTube 링크를 입력해주세요", color = Color(0xFFFF5D5D), fontSize = 12.sp)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFEEEEEE),
                        focusedBorderColor = Color(0xFF362000),
                        errorBorderColor = Color(0xFFFF5D5D)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.onEvent(ContentRegisterEvent.Submit) },
            enabled = uiState.isValidUrl,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEDF57),
                disabledContainerColor = Color(0xFFE8E8E8)
            )
        ) {
            Text(
                "학습 시작하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (uiState.isValidUrl) Color(0xFF362000) else Color(0xFFAAAAAA)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}