package com.noteVE.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import com.noteVE.domain.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Two visual styles share the same overlay host; all surfaces are tint-only, never blurred. */
val LocalVivid = staticCompositionLocalOf { true }
private val LocalAuroraPhase = staticCompositionLocalOf<State<Float>?> { null }
private val LocalOverlay = staticCompositionLocalOf<OverlayRegistry?> { null }

private class OverlayEntry(val render: @Composable () -> Unit)
private class OverlayRegistry { val entries = mutableStateListOf<OverlayEntry>() }

val isDarkSurface: Boolean
    @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.4f

@Composable
fun AuroraScaffoldRoot(content: @Composable () -> Unit) {
    val style by Settings.uiStyle.collectAsState()
    val vivid = style == Settings.STYLE_VIVID
    val registry = remember { OverlayRegistry() }
    val phase = if (vivid) rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "auroraPhase"
    ) else null
    CompositionLocalProvider(
        LocalVivid provides vivid,
        LocalOverlay provides registry,
        LocalAuroraPhase provides phase,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) { content() }
            Box(Modifier.fillMaxSize().imePadding()) {
                registry.entries.forEach { e -> key(e) { e.render() } }
            }
        }
    }
}

/** 把内容挂到根 Overlay（同窗口、在所有页面之上）。 */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    val reg = LocalOverlay.current
    val latest = rememberUpdatedState(content)
    if (reg == null) { content(); return }
    DisposableEffect(reg) {
        val e = OverlayEntry { latest.value() }
        reg.entries.add(e)
        onDispose { reg.entries.remove(e) }
    }
}

/** 极光：深邃底色 + 四团高饱和光斑漂移。相位只在绘制阶段读取 → 不重组。 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (!LocalVivid.current) {
        Box(modifier.fillMaxSize().background(cs.background))
        return
    }
    val dark = isDarkSurface
    val phaseState = LocalAuroraPhase.current
    val top = if (dark) Color(0xFF070A14) else Color(0xFFF7F8FF)
    val bottom = if (dark) Color(0xFF0D1024) else Color(0xFFEEF0FF)
    val k = if (dark) 1f else 0.78f
    val c1 = cs.primary.copy(alpha = 0.62f * k)
    val c2 = cs.tertiary.copy(alpha = 0.50f * k)
    val c3 = Color(0xFF22D3EE).copy(alpha = 0.34f * k)
    val c4 = Color(0xFFFF6FB5).copy(alpha = 0.26f * k)
    Box(
        modifier.fillMaxSize().drawBehind {
            val p = phaseState?.value ?: 0f
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            val w = size.width; val h = size.height; val r = maxOf(w, h)
            fun blob(color: Color, cx: Float, cy: Float, rad: Float) =
                drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), Offset(cx, cy), rad), rad, Offset(cx, cy))
            blob(c1, w * (0.10f + 0.22f * cos(p)), h * (0.10f + 0.08f * sin(p)), r * 0.58f)
            blob(c2, w * (0.92f + 0.12f * sin(p * 2)), h * (0.36f + 0.14f * cos(p)), r * 0.50f)
            blob(c3, w * (0.30f + 0.25f * sin(p)), h * (0.78f + 0.08f * cos(p * 2)), r * 0.46f)
            blob(c4, w * (0.80f + 0.15f * cos(p * 3)), h * (0.98f + 0.04f * sin(p)), r * 0.40f)
        }
    )
}

/**
 * 页面骨架：底层内容可滚动，顶栏以独立半透明遮罩保证可读性。
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Transparent,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var topH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    run {
        Box(modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize()) {
                AuroraBackground()
                content(PaddingValues(top = with(density) { topH.toDp() }, bottom = navBottom))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topH = it.height }
                    .background(
                        if (!LocalVivid.current) MaterialTheme.colorScheme.surface
                        else if (isDarkSurface) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                        else Color.White.copy(alpha = 0.82f)
                    )
                    .glassBottomEdge()
                    .statusBarsPadding()
            ) { topBar() }
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)) { floatingActionButton() }
        }
    }
}

/** Tint-only overlay surface, matching the former 90% fallback appearance. */
fun Modifier.liquidGlass(
    shape: Shape,
    edge: Boolean = true,
): Modifier = composed {
    val vivid = LocalVivid.current
    val glass = this.clip(shape).background(MaterialTheme.colorScheme.surface.copy(alpha = if (vivid) 0.90f else 1f))
    if (edge && vivid) glass.glassEdge(shape) else glass
}

/** 半透明玻璃卡（列表内，不逐卡模糊以保 120Hz）。 */
fun Modifier.glassCard(shape: Shape, strength: Float = 1f): Modifier = composed {
    if (!LocalVivid.current) return@composed this.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
    val dark = isDarkSurface
    val fill = if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f * strength), Color.White.copy(alpha = 0.04f * strength)))
    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.82f * strength), Color.White.copy(alpha = 0.58f * strength)))
    this.clip(shape).background(fill).glassEdge(shape)
}

/** 斜向高光描边：左上亮、右下隐。 */
fun Modifier.glassEdge(shape: Shape): Modifier = composed {
    if (!LocalVivid.current) return@composed this
    val dark = isDarkSurface
    this.border(
        0.8.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.30f else 0.95f),
                Color.White.copy(alpha = if (dark) 0.04f else 0.25f),
                Color.White.copy(alpha = if (dark) 0.12f else 0.55f),
            )
        ),
        shape
    )
}

private fun Modifier.glassBottomEdge(): Modifier = composed {
    val c = if (LocalVivid.current) Color.White.copy(alpha = if (isDarkSurface) 0.14f else 0.7f) else Color.Transparent
    drawBehind {
        drawLine(c, Offset(size.width * 0.12f, size.height - 0.5f), Offset(size.width * 0.88f, size.height - 0.5f), 1f)
    }
}

@Composable
fun brandGradient(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(if (LocalVivid.current) listOf(cs.primary, cs.tertiary) else listOf(cs.primary, cs.primary))
}

// ───────────────────────── 玻璃菜单 ─────────────────────────

/** 替代 DropdownMenu：锚定父布局，渲染到根 Overlay，半透明。 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.size(0.dp).onGloballyPositioned { c -> anchor = (c.parentLayoutCoordinates ?: c).boundsInRoot() })
    if (!expanded) return
    val a = anchor ?: return
    Overlay { GlassMenuSurface(a, onDismissRequest, modifier, content) }
}

@Composable
private fun GlassMenuSurface(
    a: Rect,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val margin = with(density) { 10.dp.toPx() }
        var sz by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismissRequest() } })
        val alignEnd = a.center.x > W / 2
        val below = a.bottom + sz.height + margin <= H
        val x = if (alignEnd) (a.right - sz.width) else a.left
        val y = if (below) a.bottom else (a.top - sz.height)
        val cx = x.coerceIn(margin, (W - sz.width - margin).coerceAtLeast(margin))
        val cy = y.coerceIn(margin, (H - sz.height - margin).coerceAtLeast(margin))
        val vis = remember { MutableTransitionState(false).apply { targetState = true } }
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .offset { IntOffset(cx.toInt(), cy.toInt()) }
                .onSizeChanged { sz = it }
                .alpha(if (sz == IntSize.Zero) 0f else 1f)
        ) {
            AnimatedVisibility(
                visibleState = vis,
                enter = scaleIn(
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f,
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, if (below) 0f else 1f)
                ) + fadeIn(tween(160)),
            ) {
                Column(
                    modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 196.dp, max = 288.dp)
                        .heightIn(max = with(density) { (H * 0.7f).toDp() })
                        .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                        .liquidGlass(shape)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

/** 替代 DropdownMenuItem。 */
@Composable
fun GlassMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)) { text() }
            }
        }
        if (trailingIcon != null) { Spacer(Modifier.width(8.dp)); trailingIcon() }
    }
}

// ───────────────────────── 玻璃对话框 ─────────────────────────

/** 替代 AlertDialog：同窗口玻璃卡 + 动态遮罩，参数名与 M3 保持一致。 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Overlay {
        BackHandler(onBack = onDismissRequest)
            val vis = remember { MutableTransitionState(false).apply { targetState = true } }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val maxH = maxHeight
            AnimatedVisibility(visibleState = vis, enter = fadeIn(tween(220))) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isDarkSurface) 0.36f else 0.18f))
                        .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                )
            }
            AnimatedVisibility(
                visibleState = vis,
                enter = scaleIn(spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.86f) + fadeIn(tween(180)),
            ) {
                val shape = RoundedCornerShape(32.dp)
                Column(
                    modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxH * 0.88f)
                        .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.25f))
                        .liquidGlass(shape)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                ) {
                    if (icon != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { icon() }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold)) { title() }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (text != null) {
                        Box(Modifier.weight(1f, fill = false)) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** 透明顶栏（外层 GlassScaffold 负责玻璃）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.TopAppBarColors? = null,
) {
    androidx.compose.material3.TopAppBar(
        title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions,
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent
        )
    )
}
