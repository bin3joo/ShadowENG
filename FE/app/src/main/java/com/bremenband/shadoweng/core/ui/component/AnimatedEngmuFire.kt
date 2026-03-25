package com.bremenband.shadoweng.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.bremenband.shadoweng.R
import kotlinx.coroutines.delay

@Composable
fun AnimatedEngmuFire(modifier: Modifier = Modifier) {
    val frames = listOf(R.drawable.engmu_fire_1, R.drawable.engmu_fire_2)
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(150)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Image(
        painter = painterResource(id = frames[frameIndex]),
        contentDescription = "잉무",
        modifier = modifier
    )
}