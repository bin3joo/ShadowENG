package com.bremenband.shadoweng.feature.auth.presentation

import androidx.compose.foundation.background
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
fun SetNicknameScreen(
    onComplete: () -> Unit,
    viewModel: SetNicknameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onComplete()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAF9F6)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("닉네임을 설정해주세요", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF362000))
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.input,
            onValueChange = viewModel::onInputChanged,
            placeholder = { Text("닉네임 입력") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = viewModel::confirm,
            enabled = uiState.input.isNotBlank() && !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEDF57))
        ) {
            Text("시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000))
        }
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(uiState.error!!, color = Color(0xFFFF5D5D), fontSize = 13.sp)
        }
    }
}