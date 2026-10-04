package com.noteVE.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.noteVE.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 编辑页右侧「快速滚动**手柄**」。
 *
 * ## 视觉：整个控件只有一个手柄
 *
 * **不画轨道、也不画拇指** —— 手柄的**垂直位置**即代表列表滚动位置，
 * 不再靠「灰轨道 + 蓝拇指」的对比来表达位置。
 *
 * 造型：右侧直边贴屏幕右缘、左侧上下圆角、内含上下两个直角箭头。
 * 几何与配色见 `drawable/ic_fast_scroll_handle.xml`（用贝塞尔近似圆角，
 * 规避 Android 对 `A`（arc）指令的渲染偏差）。
 *
 * ## ★ 显隐：仅在列表滚动时出现
 *
 * | 时机 | 表现 |
 * |---|---|
 * | 列表正在滚动 / 正在拖动手柄 | 淡入，完全不透明 |
 * | 停止滚动后 [LINGER_MS] | 仍可见（留出抓取窗口） |
 * | 之后 | 淡出至透明 |
 *
 * **透明时同时禁用触摸** —— 否则「看不见却可点」会造成误触
 * （这正是上一版被反馈的问题）。
 *
 * ## ★ 交互：只有按住「手柄本身」才触发
 *
 * | 触点位置 | 行为 |
 * |---|---|
 * | 落在**手柄矩形**内 | 触发快速滚动 |
 * | 落在**其它任何位置** | **不响应** —— 事件交给正文，正常滚动 |
 *
 * 实现要点：`pointerInput` 挂在**手柄自身的 Box** 上（尺寸 = 手柄尺寸），
 * 而非挂在整条右侧竖带上 —— 后者会导致「碰一下屏幕右缘就跳走」。
 *
 * 拖动时手柄**跟随手指**（按位移增量累加，不是绝对定位），
 * 因此按下瞬间不会位移；并 `consume()` 独占手势，避免正文跟着一起滚。
 *
 * ## 尺寸（按屏宽自适应）
 *
 * 基准规格：参考屏 1440×3200，手柄 128×192（宽高比 2:3）
 *
 * ```
 * 手柄宽 = 屏宽 × 10.22%      （= 8.89% × 1.15，按需求放大 15%）
 * 手柄高 = 手柄宽 × 1.5
 * ```
 *
 * 内部元素由 VectorDrawable 自动等比缩放，无需重算。
 *
 * ## 深色模式
 *
 * - 资源层：`drawable-night/` 限定符（系统深色时自动生效）
 * - Compose 层：按当前主题亮度**显式选择**深色资源 ——
 *   覆盖「应用内手动切深色、但系统仍是浅色」的情形（限定符只认系统配置）
 */
@Composable
fun FastScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    // 内容不足一屏 → 无可滚动 → 不显示
    if (scrollState.maxValue <= 0) return

    val density = LocalDensity.current
    val conf = LocalConfiguration.current

    // ★ 手柄尺寸：宽 = 屏宽 × 10.22%，高 = 宽 × 1.5
    val handleWpx = with(density) { conf.screenWidthDp.dp.toPx() } * HANDLE_W_RATIO
    val handleW = with(density) { handleWpx.toDp() }
    val handleH = handleW * 1.5f
    val handleHpx = with(density) { handleH.toPx() }

    // ★ 深色模式：按当前主题亮度显式选资源（不依赖资源限定符）
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val handleRes = if (dark) R.drawable.ic_fast_scroll_handle_dark
    else R.drawable.ic_fast_scroll_handle

    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }

    /** 停止滚动后的保持时间（留出「抓取手柄」的窗口）。 */
    var lingers by remember { mutableStateOf(false) }

    /** 拖动期间由手指驱动的手柄顶边（px）；< 0 表示「跟随滚动状态」。 */
    var dragTop by remember { mutableFloatStateOf(-1f) }

    val scope = rememberCoroutineScope()

    // ★ 是否应显示：滚动中 或 正在拖手柄
    val scrolling = scrollState.isScrollInProgress || dragging

    // 滚动停止后保持 [LINGER_MS] 再淡出
    LaunchedEffect(scrolling) {
        if (scrolling) {
            lingers = true
        } else {
            delay(LINGER_MS)
            lingers = false
        }
    }

    val targetAlpha = if (scrolling) 1f else if (lingers) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(if (scrolling) 120 else 320),
        label = "fastScrollHandleAlpha"
    )

    // 手柄可滑动行程 = 轨道高 − 手柄高
    val travel = (trackSize.height - handleHpx).coerceAtLeast(0f)

    // ★ 位置来源：拖动中由 dragTop 驱动（跟手，无延迟）；否则由滚动状态驱动
    val handleTopPx = if (dragging && dragTop >= 0f) dragTop
    else travel * progressOf(scrollState)

    // 外层只用于**测量可滚动区域高度**，本身不接受触摸
    //（无 pointerInput 的 Box 不消费触摸，事件穿透给正文）
    Box(
        modifier
            .width(handleW)
            .fillMaxHeight()
            .onSizeChanged { trackSize = it }
    ) {
        // 完全透明时不渲染、不可触摸（避免「看不见却能点」的误触）
        if (alpha <= 0.01f) return@Box

        // ── 唯一可触摸元素：手柄 ─────────────────────────────────
        //    ★ pointerInput 挂在此处 → 只有按住手柄才响应
        Box(
            Modifier
                // lambda 版 offset：拖动中只重绘、不重组，手势不会被中断
                .offset { IntOffset(0, handleTopPx.roundToInt()) }
                .width(handleW)
                .height(handleH)
                .pointerInput(scrollState, handleHpx) {
                    // 拖动开始时读取实时值，避免闭包捕获过期数据
                    fun currentTravel(): Float =
                        (trackSize.height - handleHpx).coerceAtLeast(0f)

                    detectDragGestures(
                        onDragStart = {
                            dragTop = currentTravel() * progressOf(scrollState)
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            dragTop = -1f   // 交还滚动状态驱动（位置一致，无跳变）
                        },
                        onDragCancel = {
                            dragging = false
                            dragTop = -1f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()   // 独占手势，正文不再跟着滚
                            val tv = currentTravel()
                            val top = (dragTop + dragAmount.y).coerceIn(0f, tv)
                            dragTop = top
                            val frac = if (tv > 0f) top / tv else 0f
                            scope.launch {
                                scrollState.scrollTo(
                                    (scrollState.maxValue * frac).roundToInt()
                                )
                            }
                        }
                    )
                }
        ) {
            Image(
                painter = painterResource(handleRes),
                contentDescription = stringResource(R.string.fast_scroll_hint),
                modifier = Modifier
                    .width(handleW)
                    .height(handleH)
                    .alpha(alpha)
            )
        }
    }
}

/**
 * 手柄宽 / 屏宽。
 *
 * 基准值 8.89%（实测自参考规格 128 / 1440），
 * 按需求**整体放大 15%** → 10.22%。宽高比保持 2:3 不变。
 */
private const val HANDLE_W_RATIO = 0.0889f * 1.15f

/** 停止滚动后手柄继续显示的时长（抓取窗口）。 */
private const val LINGER_MS = 1500L

/** 当前滚动比例（0..1）。 */
private fun progressOf(scrollState: ScrollState): Float =
    if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
