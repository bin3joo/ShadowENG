package com.bremenband.shadoweng.feature.study.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Step $currentStep / $totalSteps",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF362000)
            )
        }
        LinearProgressIndicator(
            progress = { currentStep / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFFF5D5D),
            trackColor = Color(0xFFEEEEEE)
        )
    }
}