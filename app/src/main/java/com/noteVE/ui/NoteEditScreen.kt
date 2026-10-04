package com.noteVE.ui

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.text.Editable
import android.text.SpannedString
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.noteVE.domain.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noteVE.R
import com.noteVE.backup.BackupService
import com.noteVE.domain.RepeatRule
import com.noteVE.domain.Block
import com.noteVE.domain.FileKinds
import com.noteVE.domain.FileOpener
import com.noteVE.domain.HtmlEx
import com.noteVE.domain.RichTextActions
import com.noteVE.domain.VoicePlayer
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(noteId: Long?, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: NoteEditViewModel = viewModel(factory = viewModelFactory { initializer { NoteEditViewModel(app, noteId) } })
    val loaded by vm.loaded.collectAsState()
    val title by vm.title.collectAsState()
    val blocks by vm.blocks.collectAsState()
    val reminderAt by vm.reminderAt.collectAsState()
    val repeatRule by vm.repeatRule.collectAsState()
    val pinned by vm.pinned.collectAsState()
    val focusReq by vm.focusRequest.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastEmittedMap = remember { mutableStateMapOf<Int, String>() }
    var focusedEditText by remember { mutableStateOf<EditText?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val voicePlayer = remember { VoicePlayer(scope) }
    DisposableEffect(Unit) { onDispose { voicePlayer.stop() } }

    // 深色模式判定（与 NoteApp.kt 同逻辑）：供富文本渲染时把近黑文字转为白色
    val themeSetting by Settings.theme.collectAsState()
    val darkMode = when (themeSetting) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    // 富文本工具栏折叠状态（折叠后腾出正文空间，顶栏出现展开按钮）
    var toolbarCollapsed by remember { mutableStateOf(false) }

    /** 只读模式：禁止一切编辑（会话内有效，退出页面/重启即重置）。 */
    val readOnly by vm.readOnly.collectAsState()
    /** 正文滚动状态（供右侧快速定位条驱动）。 */
    val contentScroll = rememberScrollState()
    var showReminder by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var voiceTempFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var blockAction by remember { mutableStateOf<Int?>(null) } // (index, isAudio)
    var confirmDelete by remember { mutableStateOf(false) }

    fun format(action: (Editable) -> Unit) {
        val et = focusedEditText ?: return
        action(et.editableText)
        val html = HtmlEx.toHtml(et.text)
        lastEmittedMap[focusedIndex] = html
        vm.updateTextBlock(focusedIndex, html)
    }

    // 插入锚点：在弹出相册/录音弹窗「之前」锁定当前聚焦的文字块与光标，
    // 避免选择器抢占焦点后位置丢失（这正是图片/录音总被垫在最底下的根因）。
    var anchorIndex by remember { mutableIntStateOf(0) }
    var anchorEdit by remember { mutableStateOf<EditText?>(null) }
    fun captureAnchor() { anchorIndex = focusedIndex; anchorEdit = focusedEditText }

    // ★ 格式化操作（插入链接 / 文字颜色）的「选区冻结」：
    //   点击工具栏按钮 → 弹出对话框 → 对话框夺走 EditText 焦点 → selection 丢失。
    //   故在点击瞬间冻结 (块索引, 编辑框实例, 选区起止)，确认时只用这份冻结值。
    var frozenEdit by remember { mutableStateOf<EditText?>(null) }
    var frozenIndex by remember { mutableIntStateOf(-1) }
    var frozenRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    /** 冻结当前聚焦编辑框的选区（若没有焦点则清空，避免误用旧值）。 */
    fun freezeSelection() {
        val et = focusedEditText
        if (et == null) {
            frozenEdit = null; frozenIndex = -1; frozenRange = null
            return
        }
        val st = et.selectionStart
        val en = et.selectionEnd
        frozenEdit = et
        frozenIndex = focusedIndex
        frozenRange = if (st >= 0 && en >= 0) (st to en) else null
    }

    /**
     * 用冻结的选区执行格式化，并把结果同步回 ViewModel。
     * 选区来自 [freezeSelection]，不依赖对话框期间的 selection 状态。
     */
    fun applyFrozen(action: (Editable, Pair<Int, Int>?) -> Unit) {
        val et = frozenEdit ?: return
        val idx = frozenIndex
        if (idx < 0) return
        action(et.editableText, frozenRange)
        val html = HtmlEx.toHtml(et.text)
        lastEmittedMap[idx] = html
        vm.updateTextBlock(idx, html)
    }

    /**
     * 在光标处插入块（矢量排布核心）。
     * 光标所在文字块按光标拆成「前段/后段」，块插入其间；无光标（标题/空笔记）则追加到末尾。
     */
    fun insertAtCursor(block: Block) {
        val et = anchorEdit
        val idx = anchorIndex
        if (et != null && idx in blocks.indices && blocks[idx] is Block.Text) {
            val text = et.text
            val a = et.selectionStart
            val b = et.selectionEnd
            val valid = a >= 0 && b >= 0
            val lo = (if (valid) minOf(a, b) else text.length).coerceIn(0, text.length)
            val hi = (if (valid) maxOf(a, b) else text.length).coerceIn(0, text.length)
            val beforeHtml = HtmlEx.toHtml(SpannedString(text.subSequence(0, lo)))
            val afterHtml = HtmlEx.toHtml(SpannedString(text.subSequence(hi, text.length)))
            vm.splitAndInsert(idx, beforeHtml, block, afterHtml)
            // 锚点下移到「后段」，连续插入时位置正确
            anchorIndex = idx + 2
            anchorEdit = null
        } else {
            // 未出现光标 / 光标在标题：默认插入到最下方
            vm.insertBlock(blocks.size, block)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            scope.launch {
                val name = vm.importImage(u)
                insertAtCursor(Block.Image(name))
            }
        }
    }

    // 任意文件选择器（SAF，*/*）：按扩展名自动分流为 视频/音频/其它
    val filePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { u ->
            if (readOnly) {
                toast(context, R.string.readonly_blocked)
            } else {
                scope.launch {
                    val name = vm.importAnyFile(u)
                    // 选中图片时走「图片」通道（满宽显示）；其余一律为「文件块」
                    // （视频/音频/文档由 FileKinds 按扩展名区分卡片样式）
                    if (FileKinds.isImage(name)) insertAtCursor(Block.Image(name))
                    else insertAtCursor(Block.File(name))
                }
            }
        }
    }

    val resultReceiver = remember {
        object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                Toast.makeText(context, resultData?.getString(BackupService.EXTRA_MSG) ?: "", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var pendingExportId by remember { mutableStateOf<Long?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { u -> pendingExportId?.let { id -> startExport(context, BackupService.ACTION_EXPORT_SINGLE, u, resultReceiver, id) } }
    }
    fun exportNote() {
        scope.launch {
            val id = vm.save()
            pendingExportId = id
            exportLauncher.launch("note_$id.noteapp")
        }
    }

    BackHandler {
        scope.launch { vm.save(); onBack() }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val imeVisible = rememberImeVisible()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { scope.launch { vm.save(); onBack() } }) {
                        Icon(painterResource(R.drawable.ic_action_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    // 折叠状态下：在闹钟左边显示「展开工具栏」按钮（向下箭头）
                    AnimatedVisibility(
                        visible = imeVisible && toolbarCollapsed,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f)
                    ) {
                        IconButton(onClick = { toolbarCollapsed = false }) {
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                stringResource(R.string.expand_toolbar),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showReminder = true }) { Icon(painterResource(R.drawable.ic_editor_reminder), stringResource(R.string.reminder_item)) }
                    // 只读开关（图标带切换动画）
                    IconButton(onClick = {
                        vm.toggleReadOnly()
                        toast(context, if (readOnly) R.string.readonly_off else R.string.readonly_on)
                    }) {
                        Crossfade(
                            targetState = readOnly,
                            animationSpec = tween(220),
                            label = "eyeToggle"
                        ) { ro ->
                            Icon(
                                painterResource(
                                    if (ro) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
                                ),
                                stringResource(R.string.readonly_toggle),
                                tint = if (ro) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (readOnly) toast(context, R.string.readonly_blocked)
                        else { captureAnchor(); insertOpen = true }
                    }) { Icon(painterResource(R.drawable.ic_action_add), stringResource(R.string.insert)) }
                    DropdownMenu(expanded = insertOpen, onDismissRequest = { insertOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.add_image)) }, onClick = {
                            insertOpen = false
                            if (readOnly) toast(context, R.string.readonly_blocked) else imagePicker.launch("image/*")
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.insert_voice)) }, onClick = { insertOpen = false; voiceTempFile = vm.createTempRecordingFile(); showVoiceDialog = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.insert_file)) }, onClick = {
                            insertOpen = false
                            if (readOnly) toast(context, R.string.readonly_blocked) else filePicker.launch(arrayOf("*/*"))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_note)) }, onClick = { insertOpen = false; exportNote() })
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(painterResource(R.drawable.ic_action_more), stringResource(R.string.more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (pinned) R.string.unpin else R.string.pin)) }, onClick = { vm.updatePinned(!pinned); menuOpen = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_note)) }, onClick = { menuOpen = false; exportNote() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; confirmDelete = true })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 工具栏用动画展开/收起（此前是瞬间插入，会顶动正文造成「画面飞出」）
            // ★ expandVertically 的默认 expandFrom = Alignment.Bottom，
            //   会让内容在展开动画期间先出现在容器【上方】（即屏幕外）→ 表现为「飞出屏幕点不到」。
            //   必须显式指定 Top，让工具栏从顶部向下生长。
            AnimatedVisibility(
                visible = imeVisible && !toolbarCollapsed,
                enter = expandVertically(
                    animationSpec = tween(220), expandFrom = Alignment.Top
                ) + fadeIn(tween(220)),
                exit = shrinkVertically(
                    animationSpec = tween(180), shrinkTowards = Alignment.Top
                ) + fadeOut(tween(180))
            ) {
                FormattingToolbar(
                    onUnderline = { format { RichTextActions.toggleUnderline(it) } },
                    onStrike = { format { RichTextActions.toggleStrike(it) } },
                    onBullet = { format { RichTextActions.toggleBullet(it) } },
                    onLink = { freezeSelection(); showLink = true },
                    onColor = { freezeSelection(); showColor = true },
                    onCollapse = { toolbarCollapsed = true }
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // 末个文字块至少撑满剩余视口：否则下方空白不属于任何 EditText，点击无响应、无光标。
                // ★用「历史最大高度」而非当前高度：输入法弹出会令 maxHeight 变小，
                //   若跟着变小会导致内容重排 + 滚动位置跳变（就是「画面飞出」）。
                val rawMin = (maxHeight - 108.dp).coerceAtLeast(120.dp)
                var stableMin by remember { mutableStateOf(rawMin) }
                if (rawMin > stableMin) stableMin = rawMin
                val bodyMin = stableMin
                Column(Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                    BasicTextField(
                        value = title,
                        onValueChange = { vm.updateTitle(it) },
                        // ★ 必须显式指定 color：BasicTextField 的 textStyle 不指定时
                        //   不会跟随 MaterialTheme 的 onSurface，深色下仍是黑字。
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) {
                                    Text(stringResource(R.string.title_hint), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                                }
                                inner()
                            }
                        }
                    )
                    // 标题与正文的分界线（跟随深浅色主题）
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    blocks.forEachIndexed { index, block ->
                        when (block) {
                            is Block.Text -> TextBlockEditor(
                                initialHtml = block.html,
                                lastEmittedMap = lastEmittedMap,
                                blockIndex = index,
                                focusRequest = focusReq,
                                onFocusConsumed = { vm.consumeFocus(index) },
                                onHtmlChange = { vm.updateTextBlock(index, it) },
                                onFocused = { et -> focusedEditText = et; focusedIndex = index; vm.setInsertIndex(index) },
                                minHeight = if (index == blocks.lastIndex) bodyMin else 0.dp,
                                darkMode = darkMode,
                                readOnly = readOnly,
                                modifier = Modifier.fillMaxWidth()
                            )
                            is Block.Image -> ImageBlock(
                                file = vm.resolveImage(block.path),
                                onLongPress = { blockAction = index }
                            )
                            is Block.Audio -> VoiceRecordCard(
                                name = block.path,
                                file = vm.resolveVoice(block.path),
                                player = voicePlayer,
                                onLongPress = { blockAction = index }
                            )
                            is Block.File -> FileBlockCard(
                                name = block.path,
                                file = vm.resolveFile(block.path),
                                onLongPress = { blockAction = index },
                                onOpen = {
                                    FileOpener.openWith(context, FileOpener.DIR_IMAGES, block.path)
                                }
                            )
                        }
                    }
                    // 底部留白（约一行），避免图片/录音垫底后贴着屏幕边缘、点不到下方
                    Spacer(Modifier.height(56.dp))
                }

                // 右侧快速定位条（内容可滚动时出现；触摸高亮、拖动定位）
                FastScrollbar(
                    scrollState = contentScroll,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    if (showReminder) {
        ReminderDialog(reminderAt, repeatRule, { at, rule -> vm.updateReminder(at, rule); showReminder = false }, { showReminder = false })
    }
    if (showLink) {
        LinkDialog({ url ->
            // 使用点击工具栏瞬间冻结的选区：不依赖对话框期间的 selection
            applyFrozen { editable, range -> RichTextActions.applyLink(editable, url, range) }
            showLink = false
        }, { showLink = false })
    }
    if (showColor) {
        ColorDialog({ c ->
            applyFrozen { editable, range -> RichTextActions.applyColor(editable, c, range) }
            showColor = false
        }, { showColor = false })
    }
    if (showVoiceDialog && voiceTempFile != null) {
        RecordingDialog(
            tempFile = voiceTempFile!!,
            onInsert = { nm ->
                val fn = vm.finalizeRecording(voiceTempFile!!, nm)
                insertAtCursor(Block.Audio(fn))
                showVoiceDialog = false
            },
            onCancel = { voiceTempFile!!.delete(); showVoiceDialog = false }
        )
    }
    // 块操作弹窗（详情可滚动 + 只读拦截）
    blockAction?.let { idx ->
        val block = blocks.getOrNull(idx)
        if (block != null) {
            BlockActionDialog(
                block = block,
                readOnly = readOnly,
                onRename = { p -> blockAction = null; renameTarget = p },
                onDelete = { blockAction = null; vm.removeBlock(idx) },
                onDismiss = { blockAction = null },
                onBlocked = { toast(context, R.string.readonly_blocked) }
            )
        } else {
            blockAction = null
        }
    }
    if (renameTarget != null) {
        RenameDialog(renameTarget!!, { newName ->
            val t = renameTarget!!
            val isVoiceBlock = blocks.any { it is Block.Audio && it.path == t }
            if (isVoiceBlock) vm.renameVoice(t, newName) else vm.renameFile(t, newName)
            renameTarget = null
        }, { renameTarget = null })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm, title.ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                    scope.launch { onBack() }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun FormattingToolbar(
    onUnderline: () -> Unit, onStrike: () -> Unit, onBullet: () -> Unit, onLink: () -> Unit, onColor: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            // ★ 明确高度 56dp：图标触控区是 48dp，若不给高度约束，
            //   父容器按固定高度测量时会把工具栏压扁成一条细缝（表现为「只露出一点」）。
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            // ★ 容器不可获取焦点：否则点击格式图标会把焦点从编辑框抢走
            //   → 输入法收起 → 工具栏随 imeVisible 变化而闪退
            .focusProperties { canFocus = false },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：可横向滚动的格式图标
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIcon(R.drawable.ic_editor_underline, R.string.underline, onUnderline)
            ToolbarIcon(R.drawable.ic_editor_strikethrough, R.string.strikethrough, onStrike)
            ToolbarIcon(R.drawable.ic_editor_bullet_list, R.string.bullet_list, onBullet)
            ToolbarIcon(R.drawable.ic_editor_link, R.string.insert_link, onLink)
            ToolbarIcon(R.drawable.ic_editor_color, R.string.text_color, onColor)
        }
        // 右侧：固定「折叠」按钮（向上箭头），不随图标滚动
        ToolbarIcon(R.drawable.ic_chevron_up, R.string.collapse_toolbar, onCollapse)
    }
}

@Composable
private fun ToolbarIcon(iconRes: Int, descRes: Int, onClick: () -> Unit) {
    // 触控区 >=48dp，图标 22dp
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painterResource(iconRes),
            stringResource(descRes),
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(
    currentAt: Long?, currentRule: String?,
    onConfirm: (Long?, String?) -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rule by remember { mutableStateOf(currentRule ?: RepeatRule.NONE) }
    var time by remember { mutableStateOf(currentAt ?: (System.currentTimeMillis() + 3600000)) }
    val cal = remember { Calendar.getInstance().apply { timeInMillis = time } }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    fun showPicker() {
        DatePickerDialog(
            context,
            { _, y, m, day ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(context, { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    time = cal.timeInMillis
                },
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_reminder)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 提醒时间用矩形框起来并居中，明确可编辑
                Box(
                    Modifier.fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { showPicker() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(df.format(time), style = MaterialTheme.typography.titleMedium)
                }
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(RepeatRule.NONE, RepeatRule.DAILY, RepeatRule.WEEKLY, RepeatRule.MONTHLY, RepeatRule.YEARLY).forEach { r ->
                        FilterChip(selected = rule == r, onClick = { rule = r }, label = { Text(repeatLabel(r)) })
                    }
                }
                if (currentAt != null) {
                    TextButton(onClick = { onConfirm(null, null) }) { Text(stringResource(R.string.clear_reminder)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(time, rule) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
@Composable
private fun LinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_link)) },
        text = { OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(url) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    // 深色模式下把"黑色"色块显示为白色（存储值不变，仅显示适配），其余颜色不动
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = listOf(
        if (dark) Color(0xFFFFFFFF) else Color(0xFF000000),   // 默认色（黑↔白）
        Color(0xFFE53935), Color(0xFFFF9800), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_color)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { c ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onPick(c.toArgb()) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // 去掉扩展名后再编辑（确认时由各 rename 接口补回原扩展名）
    var text by remember {
        mutableStateOf(if (current.contains('.')) current.substringBeforeLast('.') else current)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun repeatLabel(r: String): String = when (r) {
    RepeatRule.NONE -> stringResource(R.string.none_repeat)
    RepeatRule.DAILY -> stringResource(R.string.daily)
    RepeatRule.WEEKLY -> stringResource(R.string.weekly)
    RepeatRule.MONTHLY -> stringResource(R.string.monthly)
    RepeatRule.YEARLY -> stringResource(R.string.yearly)
    else -> r
}

private fun startExport(context: Context, action: String, uri: Uri, receiver: ResultReceiver, noteId: Long) {
    val intent = Intent(context, BackupService::class.java).apply {
        this.action = action
        putExtra(BackupService.EXTRA_URI, uri)
        putExtra(BackupService.EXTRA_NOTE_ID, noteId)
        putExtra(BackupService.EXTRA_RESULT, receiver)
    }
    ContextCompat.startForegroundService(context, intent)
}

/** 可靠的输入法可见性检测（基于窗口可视区域 vs 根视图高度）。 */
@Composable
private fun rememberImeVisible(): Boolean {
    val view = LocalView.current
    val imeVisible = remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            // ★ 必须用 displayMetrics.heightPixels（屏幕物理高，稳定）
            //   而不是 view.rootView.height —— 后者在 adjustResize 下会随输入法一起缩小，
            //   算出 insets≈0 → imeVisible 反复翻转 → 工具栏 enter/exit 动画来回播放
            //   （表现为「工具栏往上飞/闪烁」）。
            val screenHeight = view.resources.displayMetrics.heightPixels
            val bottomInset = screenHeight - r.bottom
            imeVisible.value = bottomInset > screenHeight * 0.15
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return imeVisible.value
}

/** 轻量 Toast。 */
private fun toast(context: android.content.Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
}
