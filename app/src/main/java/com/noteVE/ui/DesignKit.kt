package com.noteVE.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind

/**
 * 全应用统一的设计组件 —— 「iOS Settings 分组列表」风格。
 *
 * 所有二级页（设置 / 授权相关 / 存储 / 关于）共用这一套，确保视觉语言一致。
 *
 * ## 规格（源自 Shizuku + Thanox 实测）
 * | 元素 | 规格 |
 * |---|---|
 * | 分组卡 | `14dp` 圆角 · `surfaceContainerLow` 底 · 左右 `16dp` |
 * | 分组标题 | `labelSmall` + primary 色 · 上下 `8dp` |
 * | 行高 | `≥56dp` · 内边距 `14/12dp` |
 * | **图标色块** | **`30dp` 圆角 `8dp` 方块 + `18dp` 白色图标** |
 * | 分隔线 | `0.5dp` · 起点缩进 `58dp`（对齐图标右侧） |
 * | 脚注 | `bodySmall` · 缩进 `32dp` |
 *
 * 全部颜色取自 [MaterialTheme]，深浅色自动适配。
 */

/** 图标色块（30dp / 8dp 圆角 / 18dp 白图标）——列表行的标志性左端元素。 */
@Composable
fun IconBlock(
    painterRes: Int,
    blockColor: Color,
    size: androidx.compose.ui.unit.Dp = 30.dp,
) {
    Box(
        Modifier
            .size(size)
            .background(blockColor.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(painterRes),
            contentDescription = null,
            tint = blockColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 分组容器：标题 + 圆角卡 + 可选脚注。 */
@Composable
fun SettingsGroup(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        header?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = content
        )
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** 行内分隔线（缩进到图标右侧）。 */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 14.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * 通用列表行：图标色块 + 标题 + 副标题 + 尾部内容。
 *
 * @param trailing 尾部区域（开关 / 数值 / 箭头…）
 * @param onClick  为 null 时不可点击
 */
@Composable
fun SettingsRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    blockColor: Color? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    clickWhole: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (onClick != null) Modifier.clickable { onClick() }
                    else if (clickWhole != null) Modifier.clickable(enabled = enabled) { clickWhole() }
                    else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBlock(
                painterRes = iconRes,
                blockColor = blockColor ?: MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (showDivider) RowDivider()
    }
}

/** 带开关的行（授权相关页用）。 */
@Composable
fun SettingsSwitchRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    blockColor: Color? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    SettingsRow(
        iconRes = iconRes,
        title = title,
        subtitle = subtitle,
        blockColor = blockColor,
        onClick = null,
        showDivider = showDivider,
        enabled = enabled,
        clickWhole = if (enabled) onToggle else null,
        trailing = {
            Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    )
}


/** 导航行：尾部 chevron，整行可点。 */
@Composable
fun SettingsNavRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    blockColor: Color? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(
        iconRes = iconRes, title = title, subtitle = subtitle, blockColor = blockColor,
        onClick = onClick, showDivider = showDivider,
        trailing = {
            Icon(
                painterResource(com.noteVE.R.drawable.ic_chevron_right), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/**
 * 卡内分段选择：标题行 + 胶囊分段。选中块用 primary 色滑块（spring 位移，仅 graphicsLayer/offset 绘制）。
 */
@Composable
fun SettingsChoiceRow(
    iconRes: Int,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    blockColor: Color? = null,
    showDivider: Boolean = true,
) {
    Column {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlock(iconRes, blockColor ?: MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            SegmentedPill(options, selected, onSelect)
        }
        if (showDivider) RowDivider()
    }
}

@Composable
fun SegmentedPill(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val pos by androidx.compose.animation.core.animateFloatAsState(idx.toFloat(), Motion.settle(), label = "seg")
    val selColor = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(3.dp)
    ) {
        val n = options.size
        Box(
            Modifier.matchParentSize().drawBehind {
                val w = size.width / n
                drawRoundRect(
                    color = selColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w * pos, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                )
            }
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { i, (value, label) ->
                val on = i == idx
                val c by androidx.compose.animation.animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(180), label = "segc"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(selected = on, role = Role.Tab) { onSelect(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** 按压缩放（0.97 / 110ms），用于可点击卡片。 */
@Composable
fun rememberPressScale(interaction: androidx.compose.foundation.interaction.InteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, tween(110), label = "press"
    )
    return s
}
