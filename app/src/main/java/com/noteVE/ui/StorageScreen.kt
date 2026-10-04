package com.noteVE.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noteVE.R
import com.noteVE.domain.FileKinds
import com.noteVE.domain.NoteRepository
import com.noteVE.domain.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 六类占用的统一色板（与主界面强调色同一色系，深浅底均可辨识）。
 * 索引：0 文本 / 1 图片 / 2 录音 / 3 视频 / 4 音频 / 5 其它
 */
private val CAT_COLORS = listOf(
    Color(0xFF3F51B5), // 文本 —— 主色靛蓝
    Color(0xFFFF9500), // 图片 —— 橙
    Color(0xFFE5484D), // 录音 —— 红
    Color(0xFF8E4EC6), // 视频 —— 紫
    Color(0xFF00A3A3), // 音频 —— 青
    Color(0xFF8A8A8E), // 其它 —— 灰
)

/** 每类对应的 drawable 图标（与色块组合成 iOS Settings 风格行）。 */
private val CAT_ICONS = listOf(
    R.drawable.ic_file_doc,     // 文本
    R.drawable.ic_file_image,   // 图片
    R.drawable.ic_voice_mic,    // 录音
    R.drawable.ic_file_video,   // 视频
    R.drawable.ic_file_audio,   // 音频
    R.drawable.ic_file_doc,     // 其它
)

/**
 * 存储二级页（统一样式：与设置 / 授权相关页同一套 [SettingsGroup] / [SettingsRow]）。
 *
 * - 总览：合计 + 横向占比条 + 图例
 * - 列表：每个笔记一行（图标色块 + 名称 + 体积 + 箭头），按占用降序
 * - 详情：六类明细（彩色块 + 名称 + 体积）+ 底部跳转笔记
 *
 * 缩略图缓存单独标注（可被系统清理）。深色模式与中英双语均支持。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = remember { NoteRepository(context.applicationContext) }
    var selected by remember { mutableStateOf<StorageStats.NoteUsage?>(null) }

    val summary by produceState<StorageStats.Summary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            StorageStats.compute(context, repo.getAllNotes())
        }
    }

    val detail = selected
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (detail == null) R.string.storage else R.string.storage_detail))
                },
                navigationIcon = {
                    IconButton(onClick = { if (detail != null) selected = null else onBack() }) {
                        Icon(painterResource(R.drawable.ic_action_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val s = summary
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (detail == null) {
            Overview(s, padding, onPick = { selected = it })
        } else {
            Detail(detail, padding, onOpenNote = { onOpenNote(detail.noteId) })
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 总览
// ─────────────────────────────────────────────────────────────
@Composable
private fun Overview(
    s: StorageStats.Summary,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onPick: (StorageStats.NoteUsage) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item {
            SettingsGroup(header = stringResource(R.string.storage_total)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        FileKinds.formatSize(s.total),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    UsageBar(s.text, s.image, s.voice, s.video, s.audio, s.other)
                    Spacer(Modifier.height(12.dp))
                    Legend(s)
                }
            }
        }

        // 笔记列表：所有行放进【同一个分组】（iOS Settings 风格），行间用缩进分隔线
        item {
            SettingsGroup(header = stringResource(R.string.storage_by_note)) {
                if (s.notes.isEmpty()) {
                    Text(
                        stringResource(R.string.no_notes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    s.notes.forEachIndexed { i, n ->
                        SettingsRow(
                            iconRes = R.drawable.ic_note,
                            title = n.title.ifBlank { stringResource(R.string.untitled) },
                            blockColor = CAT_COLORS[0],
                            onClick = { onPick(n) },
                            showDivider = i != s.notes.lastIndex,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        FileKinds.formatSize(n.total),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (s.cacheBytes > 0) {
            item {
                Text(
                    stringResource(R.string.storage_cache_note, FileKinds.formatSize(s.cacheBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** 横向占比条：各分类按体积切分，大块靠右（先放小的）。 */
@Composable
private fun UsageBar(
    text: Long, image: Long, voice: Long, video: Long, audio: Long, other: Long,
) {
    val parts = listOf(text, image, voice, video, audio, other)
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (parts.sum() <= 0) return@Row
        parts.withIndex()
            .filter { it.value > 0 }
            .sortedBy { it.value }
            .forEach { (idx, v) ->
                Box(
                    Modifier
                        .weight(v.toFloat())
                        .height(14.dp)
                        .background(CAT_COLORS[idx])
                )
            }
    }
}

/** 图例：彩色小方块 + 名称 + 体积。 */
@Composable
private fun Legend(s: StorageStats.Summary) {
    val items = listOf(
        0 to s.text, 1 to s.image, 2 to s.voice, 3 to s.video, 4 to s.audio, 5 to s.other
    ).filter { it.second > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((idx, v) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CAT_COLORS[idx])
                )
                Text(
                    "  ${catName(idx)}: ${FileKinds.formatSize(v)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 详情
// ─────────────────────────────────────────────────────────────
@Composable
private fun Detail(
    n: StorageStats.NoteUsage,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onOpenNote: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                SettingsGroup(header = stringResource(R.string.storage_total)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            FileKinds.formatSize(n.total),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        UsageBar(n.text, n.image, n.voice, n.video, n.audio, n.other)
                    }
                }
            }
            item {
                SettingsGroup(header = stringResource(R.string.storage_by_category)) {
                    val rows = listOf(
                        0 to n.text, 1 to n.image, 2 to n.voice, 3 to n.video, 4 to n.audio, 5 to n.other
                    )
                    rows.forEachIndexed { i, (idx, v) ->
                        SettingsRow(
                            iconRes = CAT_ICONS[idx],
                            title = catName(idx),
                            blockColor = CAT_COLORS[idx],
                            showDivider = i != rows.lastIndex,
                            trailing = {
                                Text(
                                    FileKinds.formatSize(v),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        TextButton(
            onClick = onOpenNote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.storage_open_note))
        }
    }
}

@Composable
private fun catName(idx: Int): String = stringResource(
    when (idx) {
        0 -> R.string.cat_text
        1 -> R.string.cat_image
        2 -> R.string.cat_voice
        3 -> R.string.cat_video
        4 -> R.string.cat_audio
        else -> R.string.cat_other
    }
)
