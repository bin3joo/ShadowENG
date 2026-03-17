package com.bremenband.shadoweng.feature.study.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    val activeColor = Color(0xFFE53935)
    val inactiveColor = Color(0xFFDDDDDD)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isActive = index == currentStep

            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (isCompleted || isActive) activeColor else inactiveColor),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (index < totalSteps - 1) {
                Box(modifier = Modifier.weight(1f).height(2.dp).background(if (isCompleted) activeColor else inactiveColor))
            }
        }
    }
}