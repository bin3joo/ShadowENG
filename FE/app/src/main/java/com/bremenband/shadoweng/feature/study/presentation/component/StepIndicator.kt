package com.bremenband.shadoweng.feature.study.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int = 4, modifier: Modifier = Modifier) {
    val activeColor = Color(0xFFFF5D5D)
    val inactiveColor = Color(0xFFDDDDDD)

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // 연결선
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(3.dp)
                .align(Alignment.Center)
                .background(inactiveColor)
        )
        // 완료된 구간 선
        if (currentStep > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((currentStep - 1).toFloat() / (totalSteps - 1).toFloat())
                    .padding(horizontal = 12.dp)
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .background(activeColor)
            )
        }

        // 스텝 원
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val stepNum = index + 1
                val isDone = stepNum <= currentStep
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDone) activeColor else inactiveColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}