package com.bremenband.shadoweng.feature.content.presentation.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFAAAAAA),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "좋아하는 영상과 함께 학습을 시작해요!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = { viewModel.onEvent(ContentRegisterEvent.UrlChanged(it)) },
                    placeholder = { Text("링크를 입력해주세요!", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.url.isNotEmpty() && !uiState.isValidUrl,
                    supportingText = {
                        if (uiState.url.isNotEmpty() && !uiState.isValidUrl) {
                            Text(
                                "입력하신 주소 형식이 올바르지 않아요!",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                        focusedBorderColor = Color(0xFF1A1A1A)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.onEvent(ContentRegisterEvent.Submit) },
            enabled = uiState.isValidUrl,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
        ) {
            Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}