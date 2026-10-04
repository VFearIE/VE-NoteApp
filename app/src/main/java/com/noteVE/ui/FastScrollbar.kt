package com.noteVE.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 编辑页右侧「快速定位条」。
 *
 * ## 交互
 * - **静止**：半透明灰色细长圆角条，不影响阅读
 * - **触摸/拖动**：立即高亮为强调色，正文按比例**快速定位**到对应位置
 * - 停止拖动 1.2s 后淡出
 *
 * ## 映射
 * `拖动 y ÷ 轨道高` → 滚动比例 → `× maxValue` → 写入 [ScrollState]。
 * 拇指高度按 `viewportSize / (maxValue + viewportSize)` 计算；内容不足一屏时整条不显示。
 */
@Composable
fun FastScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var recentlyUsed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(dragging) {
        if (dragging) recentlyUsed = true
        else {
            delay(1200)
            recentlyUsed = false
        }
    }

    // 内容不足一屏 → 无可滚动 → 不显示
    if (scrollState.maxValue <= 0) return

    val alpha by animateFloatAsState(
        targetValue = if (dragging) 1f else if (recentlyUsed) 0.9f else 0.45f,
        animationSpec = tween(if (dragging) 80 else 260),
        label = "fastScrollbarAlpha"
    )

    Box(
        modifier
            .width(18.dp)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .onSizeChanged { trackSize = it }
            .pointerInput(scrollState) {
                // 把 y 换算为滚动量
                fun map(y: Float) {
                    val trackH = trackSize.height
                    if (trackH <= 0) return
                    val frac = (y / trackH).coerceIn(0f, 1f)
                    scope.launch { scrollState.scrollTo((scrollState.maxValue * frac).toInt()) }
                }
                detectDragGestures(
                    onDragStart = { off -> dragging = true; map(off.y) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ -> map(change.position.y) }
                )
            }
    ) {
        // 轨道（灰）
        Box(
            Modifier
                .align(Alignment.Center)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f * alpha))
        )

        if (trackSize.height > 0) {
            val trackPx = trackSize.height.toFloat()
            val vp = scrollState.viewportSize.toFloat().coerceAtLeast(1f)
            val total = (scrollState.maxValue + scrollState.viewportSize).toFloat().coerceAtLeast(1f)
            val thumbH = (trackPx * (vp / total)).coerceIn(
                with(density) { 30.dp.toPx() },
                trackPx
            )
            val progress =
                if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
            val thumbTop = ((trackPx - thumbH) * progress).coerceIn(0f, (trackPx - thumbH).coerceAtLeast(0f))

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { thumbTop.toDp() })
                    .width(9.dp)
                    .height(with(density) { thumbH.toDp() })
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}
