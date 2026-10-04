package com.noteVE.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noteVE.R
import com.noteVE.backup.BackupService
import com.noteVE.data.Note
import kotlinx.coroutines.delay
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onLicenses: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onStorage: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: NoteListViewModel = viewModel(factory = viewModelFactory { initializer { NoteListViewModel(app) } })
    val notes by vm.notes.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val searchVisible by vm.searchVisible.collectAsState()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    var reminderTarget by remember { mutableStateOf<Note?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    var importNoticeTitle by remember { mutableStateOf<String?>(null) }

    val resultReceiver = remember {
        object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    BackupService.R_INVALID -> { importNoticeTitle = context.getString(R.string.op_failed); importNotice = context.getString(R.string.backup_invalid) }
                    else -> Toast.makeText(context, resultData?.getString(BackupService.EXTRA_MSG) ?: "", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var pendingExportId by remember { mutableStateOf<Long?>(null) }
    val exportSingleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { u -> pendingExportId?.let { id -> startSvc(context, BackupService.ACTION_EXPORT_SINGLE, u, resultReceiver, id) } }
    }
    val exportFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { u -> startSvc(context, BackupService.ACTION_EXPORT_FULL, u, resultReceiver, 0L) }
    }
    val importSingleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> startSvc(context, BackupService.ACTION_IMPORT_SINGLE, u, resultReceiver, 0L) }
    }
    val importFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> startSvc(context, BackupService.ACTION_IMPORT_FULL, u, resultReceiver, 0L) }
    }

    fun exportNote(note: Note) {
        pendingExportId = note.id
        exportSingleLauncher.launch("note_${note.id}.noteapp")
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.notes)) },
                    actions = {
                        IconButton(onClick = { vm.toggleSearch() }) { Icon(painterResource(R.drawable.ic_action_search), stringResource(R.string.search_hint)) }
                        IconButton(onClick = { menuOpen = true }) { Icon(painterResource(R.drawable.ic_action_more), stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.full_backup)) }, onClick = { menuOpen = false; exportFullLauncher.launch("notes-backup.noteapp") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.batch_restore)) }, onClick = { menuOpen = false; importFullLauncher.launch(arrayOf("application/octet-stream", "application/zip")) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menuOpen = false; onSettings() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.storage)) }, onClick = { menuOpen = false; onStorage() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menuOpen = false; onAbout() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.open_source_licenses)) }, onClick = { menuOpen = false; onLicenses() })
                        }
                    }
                )
                if (searchVisible) {
                    TextField(
                        value = query,
                        onValueChange = { vm.setQuery(it) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_action_search), null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabOpen = true }, shape = MaterialTheme.shapes.large) { Icon(painterResource(R.drawable.ic_action_add), stringResource(R.string.new_note)) }
                DropdownMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.create_note)) }, onClick = { fabOpen = false; onNewNote() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_single)) }, onClick = { fabOpen = false; importSingleLauncher.launch(arrayOf("application/octet-stream", "application/zip")) })
                }
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 仅当列表中确实存在「未来的提醒」时才跑秒级计时器（否则 0 开销）
            // ★ 必须放在 LazyColumn 之外：LazyListScope 的 lambda 不是 @Composable 上下文
            val nowTick = rememberNowTicker(notes.any { it.reminderAt != null })
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteItem(
                        note,
                        now = nowTick,
                        onClick = { onOpenNote(note.id) },
                        onPin = { vm.togglePin(note) },
                        onReminder = { reminderTarget = note },
                        onExport = { exportNote(note) },
                        onDelete = { deleteTarget = note },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm, note.title.ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = { vm.delete(note); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    reminderTarget?.let { note ->
        ReminderDialog(
            note.reminderAt, note.repeatRule,
            { at, rule -> vm.updateReminder(note.id, at, rule); reminderTarget = null },
            { reminderTarget = null }
        )
    }
    importNotice?.let { msg ->
        AlertDialog(
            onDismissRequest = { importNotice = null; importNoticeTitle = null },
            title = importNoticeTitle?.let { t -> { Text(t) } },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { importNotice = null; importNoticeTitle = null }) { Text(stringResource(R.string.got_it)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteItem(
    note: Note,
    now: Long,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onReminder: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 按压反馈：轻微缩放（Thanox/Shizuku 一类的现代质感）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "cardPress"
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.pinned) Icon(
                    painterResource(R.drawable.ic_note_pinned), stringResource(R.string.pin),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painterResource(R.drawable.ic_action_more), stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (note.pinned) R.string.unpin else R.string.pin)) }, onClick = { menuOpen = false; onPin() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.reminder_item)) }, onClick = { menuOpen = false; onReminder() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_note)) }, onClick = { menuOpen = false; onExport() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            if (note.plainText.isNotBlank()) {
                Text(
                    note.plainText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                )
            }
            note.reminderAt?.let { at ->
                val active = at > now
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_reminder), null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (active) stringResource(R.string.next_reminder_in, formatCountdown(at - now))
                        else stringResource(R.string.reminder_completed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
            Text(
                formatTime(note.modifiedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun formatTime(ts: Long): String =
    if (ts == 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(ts)

/** 倒计时格式化：去掉前导零分段，秒始终显示。 */
/** 倒计时格式化：去掉前导零分段，秒始终显示。单位取本地化资源。 */
@Composable
private fun formatCountdown(ms: Long): String {
    val totalSec = maxOf(0, ms / 1000)
    val years = totalSec / (365 * 24 * 3600)
    val days = (totalSec % (365 * 24 * 3600)) / (24 * 3600)
    val hours = (totalSec % (24 * 3600)) / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    val y = stringResource(R.string.unit_year)
    val d = stringResource(R.string.unit_day)
    val h = stringResource(R.string.unit_hour)
    val m = stringResource(R.string.unit_min)
    val sec = stringResource(R.string.unit_sec)
    val sb = StringBuilder()
    var started = false
    if (years > 0 || started) { sb.append(years).append(y); started = true }
    if (days > 0 || started) { sb.append(days).append(d); started = true }
    if (hours > 0 || started) { sb.append(hours).append(h); started = true }
    if (minutes > 0 || started) { sb.append(minutes).append(m); started = true }
    sb.append(seconds).append(sec)
    return sb.toString()
}

/**
 * 「当前时间」单一来源（**提升到列表层**）。
 *
 * ★ 性能修复：此前 `rememberNow()` 定义在 [NoteItem] 内部，
 *   每张卡片各自启动一个每秒循环；笔记多时 → N 个协程 + 每秒 N 次重组，
 *   列表明显卡顿。
 *
 * 现在由列表层持有一个计时器，通过参数下传：
 *   - 无任何提醒 → 完全不跑计时器（0 开销）
 *   - 有提醒     → 每秒更新一次（倒计时要秒级精度）
 */
@Composable
private fun rememberNowTicker(enabled: Boolean): Long {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            now.value = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now.value
}

private fun startSvc(context: Context, action: String, uri: Uri, receiver: ResultReceiver, noteId: Long) {
    val intent = Intent(context, BackupService::class.java).apply {
        this.action = action
        putExtra(BackupService.EXTRA_URI, uri)
        putExtra(BackupService.EXTRA_NOTE_ID, noteId)
        putExtra(BackupService.EXTRA_RESULT, receiver)
    }
    ContextCompat.startForegroundService(context, intent)
}
