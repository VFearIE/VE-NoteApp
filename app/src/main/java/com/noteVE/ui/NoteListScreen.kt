package com.noteVE.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
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
    val backupProgress: BackupProgressState = viewModel()

    fun startBackup(action: String, uri: Uri, noteId: Long = 0L) {
        backupProgress.start(context) { receiver -> startSvc(context, action, uri, receiver, noteId) }
    }

    var pendingExportId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    val exportSingleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val id = pendingExportId
            if (id == null) backupProgress.fail()
            else startBackup(BackupService.ACTION_EXPORT_SINGLE, uri, id)
        }
    }
    val exportFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { u -> startBackup(BackupService.ACTION_EXPORT_FULL, u) }
    }
    val importSingleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> startBackup(BackupService.ACTION_IMPORT_SINGLE, u) }
    }
    val importFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> startBackup(BackupService.ACTION_IMPORT_FULL, u) }
    }

    fun exportNote(note: Note) {
        pendingExportId = note.id
        exportSingleLauncher.launch("note_${note.id}.noteapp")
    }

    GlassScaffold(

        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Column {
                GlassTopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.notes),
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, brush = brandGradient())
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        Row(
                            Modifier.padding(end = 8.dp).glassCard(androidx.compose.foundation.shape.CircleShape),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { vm.toggleSearch() }) { Icon(painterResource(R.drawable.ic_action_search), stringResource(R.string.search_hint), modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { menuOpen = true }) { Icon(painterResource(R.drawable.ic_action_more), stringResource(R.string.more), modifier = Modifier.size(20.dp)) }
                        }
                        GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            GlassMenuItem(text = { Text(stringResource(R.string.full_backup)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_perm_storage), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; exportFullLauncher.launch("notes-backup.noteapp") })
                            GlassMenuItem(text = { Text(stringResource(R.string.batch_restore)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_attach_file), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; importFullLauncher.launch(arrayOf("application/octet-stream", "application/zip")) })
                            GlassMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_perm_system), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onSettings() })
                            GlassMenuItem(text = { Text(stringResource(R.string.storage)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_storage), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onStorage() })
                            GlassMenuItem(text = { Text(stringResource(R.string.about)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_note), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onAbout() })
                            GlassMenuItem(text = { Text(stringResource(R.string.open_source_licenses)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_file_doc), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onLicenses() })
                        }
                    }
                )
                AnimatedVisibility(visible = searchVisible, enter = fadeIn(tween(160)) + expandVertically(tween(200)), exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
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
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                val fabInter = remember { MutableInteractionSource() }
                val fabScale = rememberPressScale(fabInter)
                val vivid = LocalVivid.current
                val glow = MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                        .drawBehind {
                            if (vivid) drawCircle(
                                androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent)),
                                radius = size.minDimension * 0.95f
                            )
                        }
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                        .background(brandGradient())
                        .glassEdge(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                        .clickable(interactionSource = fabInter, indication = androidx.compose.material3.ripple(color = Color.White)) { fabOpen = true },
                    contentAlignment = Alignment.Center
                ) { Icon(painterResource(R.drawable.ic_action_add), stringResource(R.string.new_note), tint = Color.White, modifier = Modifier.size(28.dp)) }
                GlassMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                    GlassMenuItem(text = { Text(stringResource(R.string.create_note)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_action_add), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { fabOpen = false; onNewNote() })
                    GlassMenuItem(text = { Text(stringResource(R.string.import_single)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_attach_file), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { fabOpen = false; importSingleLauncher.launch(arrayOf("application/octet-stream", "application/zip")) })
                }
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.enterReveal(0).padding(24.dp)) {
                    Box(
                        Modifier.size(72.dp).clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(if (query.isNotBlank()) R.drawable.ic_action_search else R.drawable.ic_note), null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        stringResource(R.string.empty_notes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else {
            // 仅当列表中确实存在「未来的提醒」时才跑秒级计时器（否则 0 开销）
            // ★ 必须放在 LazyColumn 之外：LazyListScope 的 lambda 不是 @Composable 上下文
            val nowTick = rememberNowTicker(notes.any { it.reminderAt != null })
            // 仅首屏错位入场；之后滚动回收的卡片直接显示，避免滚动时反复淡入
            var initialReveal by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(700); initialReveal = false }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "hero") { ListHero(notes, nowTick, Modifier.enterReveal(0)) }
                itemsIndexed(notes, key = { _, n -> n.id }) { index, note ->
                    NoteItem(
                        note,
                        now = nowTick,
                        onClick = { onOpenNote(note.id) },
                        onPin = { vm.togglePin(note) },
                        onReminder = { reminderTarget = note },
                        onExport = { exportNote(note) },
                        onDelete = { deleteTarget = note },
                        modifier = Modifier.animateItem().enterReveal(index, enabled = initialReveal && index < 8)
                    )
                }
            }
        }
    }

    BackupProgressDialog(backupProgress)
    deleteTarget?.let { note ->
        GlassAlertDialog(
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
    val vivid = LocalVivid.current
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
    val accent = if (note.pinned) MaterialTheme.colorScheme.primary
        else if (note.reminderAt != null) MaterialTheme.colorScheme.tertiary else Color.Transparent
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .glassCard(cardShape)
            .drawBehind {
                if (vivid && accent != Color.Transparent) drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), endX = size.width * 0.6f)
                )
            }
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Box(
                        Modifier.padding(end = 8.dp).size(22.dp).clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_note_pinned), stringResource(R.string.pin),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    note.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painterResource(R.drawable.ic_action_more), stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = MaterialTheme.shapes.medium) {
                        GlassMenuItem(text = { Text(stringResource(if (note.pinned) R.string.unpin else R.string.pin)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_note_pinned), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onPin() })
                        GlassMenuItem(text = { Text(stringResource(R.string.reminder_item)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_reminder), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onReminder() })
                        GlassMenuItem(text = { Text(stringResource(R.string.export_note)) }, leadingIcon = { Icon(painterResource(R.drawable.ic_perm_storage), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }, onClick = { menuOpen = false; onExport() })
                        GlassMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            if (note.plainText.isNotBlank()) {
                Text(
                    note.plainText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp, end = 14.dp)
            ) {
                Text(
                    formatTime(note.modifiedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                note.reminderAt?.let { at ->
                    val active = at > now
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(painterResource(R.drawable.ic_reminder), null, tint = fg, modifier = Modifier.size(12.dp))
                        Text(
                            if (active) stringResource(R.string.next_reminder_in, formatCountdown(at - now))
                            else stringResource(R.string.reminder_completed),
                            style = MaterialTheme.typography.labelSmall,
                            color = fg, maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
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


/** 列表顶部 Hero：日期问候 + 三枚统计玻璃胶囊。 */
@Composable
private fun ListHero(notes: List<Note>, now: Long, modifier: Modifier = Modifier) {
    val cal = remember(now / 60000) { java.util.Calendar.getInstance() }
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val zh = java.util.Locale.getDefault().language == "zh"
    val greet = if (zh) when (h) { in 5..10 -> "早上好"; in 11..13 -> "中午好"; in 14..17 -> "下午好"; else -> "晚上好" }
        else when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val date = remember(now / 60000) {
        java.text.SimpleDateFormat(if (zh) "M月d日 EEEE" else "EEEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val pinned = notes.count { it.pinned }
    val upcoming = notes.count { (it.reminderAt ?: 0) > now }
    Column(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
        Text(date.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.primary)
        Text(greet, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(notes.size, stringResource(R.string.notes), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            HeroStat(pinned, stringResource(R.string.pin), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            HeroStat(upcoming, stringResource(R.string.reminder_item), Color(0xFF22D3EE), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(value: Int, label: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    val shown by animateFloatAsState(value.toFloat(), androidx.compose.animation.core.spring(stiffness = 120f), label = "stat")
    Column(
        modifier
            .glassCard(shape)
            .drawBehind {
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    androidx.compose.ui.geometry.Offset(size.width, 0f), size.width * 0.8f), size.width * 0.8f, androidx.compose.ui.geometry.Offset(size.width, 0f))
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(shown.toInt().toString(), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
