package com.noteVE.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noteVE.R
import com.noteVE.domain.Block
import com.noteVE.domain.FileKinds
import com.noteVE.domain.FileOpener
import com.noteVE.domain.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 块操作弹窗（图片 / 录音 / 任意文件）。
 *
 * 详情按类型给出：
 * | 类型 | 显示 |
 * |---|---|
 * | 图片 | 名称（含扩展名）、尺寸、存储占用 |
 * | 录音 / 音频 | 名称、尺寸、**时长**、存储占用 |
 * | 视频 | 名称、尺寸、**时长**、存储占用 |
 * | 其它文件 | 名称、尺寸、存储占用 |
 *
 * 尺寸 = 人类可读（如 `2.57 MB`）；存储占用 = 精确字节数 —— 两者互补不重复。
 * 内容可滚动（见 [BlockDetailDialog]），长文件名不会挤掉按钮。
 */
@Composable
fun BlockActionDialog(
    block: Block,
    readOnly: Boolean,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onBlocked: () -> Unit,
) {
    val context = LocalContext.current

    // 解析路径与所属目录
    val path: String? = when (block) {
        is Block.Image -> block.path
        is Block.Audio -> block.path
        is Block.File -> block.path
        else -> null
    }
    if (path == null) return

    val dir: String = when (block) {
        is Block.Audio -> FileOpener.DIR_RECORDINGS
        else -> FileOpener.DIR_IMAGES
    }
    val isImage = block is Block.Image
    val isVoice = block is Block.Audio
    val kind = remember(path) { FileKinds.kindOf(path) }
    val showDuration = !isImage && (isVoice || kind != FileKinds.Kind.DOC)

    // 文件信息（存在性 + 尺寸 + 时长）
    val info by produceState(initialValue = Triple(false, 0L, 0L), path) {
        value = withContext(Dispatchers.IO) {
            val f = java.io.File(java.io.File(context.filesDir, dir), path)
            val size = if (f.exists()) f.length() else 0L
            val dur = if (showDuration) ThumbCache.durationMs(f) else 0L
            Triple(f.exists(), size, dur)
        }
    }
    val (exists, size, duration) = info

    // ★ 先把所有文案取出：buildList 的 lambda 不是 @Composable 上下文，
    //   不能在其中直接调用 stringResource()。
    val title = when {
        isImage -> stringResource(R.string.image_block)
        isVoice -> stringResource(R.string.audio_block)
        else -> stringResource(R.string.file_block)
    }
    val lblSize = stringResource(R.string.detail_size)
    val lblDuration = stringResource(R.string.detail_duration)
    val lblUsage = stringResource(R.string.detail_usage)
    val lblOpen = stringResource(R.string.open_with)
    val lblRename = stringResource(R.string.rename)
    val lblDelete = stringResource(R.string.delete)

    val rows = buildList {
        add(lblSize to FileKinds.formatSize(size))
        if (showDuration && duration > 0) {
            add(lblDuration to FileKinds.formatDuration(duration))
        }
        add(lblUsage to "$size B")
    }

    val actions = buildList {
        if (exists) {
            add(DialogAction(label = lblOpen) {
                FileOpener.openWith(context, dir, path)
                onDismiss()
            })
        }
        // 图片不提供重命名（与既有行为一致）
        if (!isImage) {
            add(DialogAction(label = lblRename, mutating = true) { onRename(path) })
        }
        add(DialogAction(label = lblDelete, danger = true, mutating = true) { onDelete() })
    }

    BlockDetailDialog(
        title = title,
        name = path,          // 含扩展名
        rows = rows,
        actions = actions,
        onDismiss = onDismiss,
        readOnly = readOnly,
        onBlocked = onBlocked,
    )
}
