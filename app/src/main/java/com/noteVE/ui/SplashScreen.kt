package com.noteVE.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.noteVE.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动动画（VE 标志）：
 *   0–1.0s  淡入 + 从 scale 0.3 放大到 1.0
 *   1.0–1.7s 停留
 *   1.7–2.1s 淡出 + 轻微放大到 1.05 → 回调进入主界面
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        }
        scale.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))

        delay(700)

        launch {
            alpha.animateTo(0f, tween(400, easing = LinearOutSlowInEasing))
        }
        scale.animateTo(1.05f, tween(400, easing = LinearOutSlowInEasing))

        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}
