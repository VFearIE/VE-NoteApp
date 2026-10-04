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
            .background(blockColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(painterRes),
            contentDescription = null,
            tint = Color.White,
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = content
        )
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 4.dp),
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(16.dp))
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
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (onClick != null) Modifier.clickable { onClick() } else Modifier
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
                    color = MaterialTheme.colorScheme.onSurface,
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
    subtitle: String,
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
        trailing = {
            Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    )
}
