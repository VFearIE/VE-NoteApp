package com.noteVE.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 动效语言（全站统一）。
 * 进入：fade + 上移 12dp，强调减速曲线，260ms，按组错位 45ms。
 * 状态：spring（中阻尼）——只读、勾选等需要“有回应”的切换。
 * 按压：0.97 缩放 110ms。
 * 原则：只动 alpha / translation / scale（graphicsLayer），不触发重测。
 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val ENTER_MS = 260
    const val STAGGER_MS = 45
    fun <T> enter() = tween<T>(ENTER_MS, easing = Emphasized)
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/** 首次组合时淡入并上浮（仅 graphicsLayer，零布局开销）。index 决定错位延迟。 */
fun Modifier.enterReveal(index: Int = 0, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val p = remember { Animatable(0f) }
    val offPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(40L + index.coerceAtMost(8) * Motion.STAGGER_MS)
        p.animateTo(1f, Motion.enter())
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * offPx
    }
}
