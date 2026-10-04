package com.noteVE.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noteVE.R

/** 弹窗按钮：[danger] 用错误色；[mutating] 表示「会修改数据」，只读模式下禁用。 */
data class DialogAction(
    val label: String,
    val danger: Boolean = false,
    val mutating: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 块详情 + 操作弹窗（图片 / 录音 / 任意文件通用）。
 *
 * ## 内容可滚动
 * 文件名可能极长。这里把「名称 + 详情行」放进**限高滚动区**，
 * 按钮固定在最下方 —— 超长文件名不会把按钮挤出屏幕。
 *
 * ## 只读模式
 * [readOnly] = true 时，[DialogAction.mutating] 的按钮点击会触发 [onBlocked]
 * 并关闭弹窗（由调用方 Toast 提示）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockDetailDialog(
    title: String,
    name: String,
    rows: List<Pair<String, String>>,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    readOnly: Boolean = false,
    onBlocked: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 文件全名（含扩展名；可换行完整显示）
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                for ((label, value) in rows) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (a in actions) {
                    TextButton(onClick = {
                        if (readOnly && a.mutating) {
                            onBlocked?.invoke()
                            onDismiss()
                        } else {
                            a.onClick()
                        }
                    }) {
                        Text(
                            a.label,
                            color = if (a.danger) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
