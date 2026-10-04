package com.noteVE.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noteVE.R
import com.noteVE.domain.FileKinds
import com.noteVE.domain.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 任意文件卡片（复用录音卡片的小矩形样式）。
 *
 * 布局：`[左侧预览/图标] 文件名（含扩展名）`，下方灰色小字 `尺寸 [· 时长]`
 * - **视频**：左侧迷你预览图（[ThumbCache] 生成并缓存）；失败回退视频图标
 * - **音频**：音符图标
 * - **其它**：文档图标
 *
 * 交互：点击 → FileProvider「打开方式」；长按 → 详情/操作弹窗。
 */
@Composable
fun FileBlockCard(
    name: String,
    file: File,
    onLongPress: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val kind = remember(name) { FileKinds.kindOf(name) }
    val sizeBytes = remember(name) { file.length() }

    var pressed by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(if (pressed) 0.35f else 0f, label = "fileScrim")

    // 视频缩略图（缓存；失败为 null → 回退图标）
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, name) {
        value = if (kind == FileKinds.Kind.VIDEO) {
            withContext(Dispatchers.IO) {
                ThumbCache.get(context, file)?.let { f ->
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                }
            }
        } else null
    }

    // 时长（仅音视频才有意义）
    val durationMs by produceState(initialValue = 0L, name) {
        value = if (kind != FileKinds.Kind.DOC) {
            withContext(Dispatchers.IO) { ThumbCache.durationMs(file) }
        } else 0L
    }

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(name) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen() },
                    onLongPress = {
                        vibrate(context)
                        onLongPress()
                    }
                )
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：预览 / 图标
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val t = thumb
                when {
                    kind == FileKinds.Kind.VIDEO && t != null -> Image(
                        bitmap = t,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    kind == FileKinds.Kind.VIDEO -> Icon(
                        painterResource(R.drawable.ic_file_video),
                        stringResource(R.string.file_kind_video),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    kind == FileKinds.Kind.AUDIO -> Icon(
                        painterResource(R.drawable.ic_file_audio),
                        stringResource(R.string.file_kind_audio),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    else -> Icon(
                        painterResource(R.drawable.ic_file_doc),
                        stringResource(R.string.file_kind_doc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 右：名称（含扩展名）+ 尺寸/时长
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildString {
                        append(FileKinds.formatSize(sizeBytes))
                        if (durationMs > 0) {
                            append(" · ")
                            append(FileKinds.formatDuration(durationMs))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (scrimAlpha > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrimAlpha)))
        }
    }
}
