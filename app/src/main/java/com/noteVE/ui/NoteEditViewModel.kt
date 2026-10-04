package com.noteVE.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noteVE.domain.Block
import com.noteVE.domain.BlockOps
import com.noteVE.domain.BlockSerializer
import com.noteVE.domain.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** 编辑页 ViewModel：以块列表为草稿状态。 */
class NoteEditViewModel(app: Application, private val noteId: Long?) : AndroidViewModel(app) {

    private val repo = NoteRepository(app)
    private val saving = AtomicBoolean(false)

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _blocks = MutableStateFlow<List<Block>>(emptyList())
    val blocks: StateFlow<List<Block>> = _blocks.asStateFlow()

    private val _reminderAt = MutableStateFlow<Long?>(null)
    val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()
    private val _repeatRule = MutableStateFlow<String?>(null)
    val repeatRule: StateFlow<String?> = _repeatRule.asStateFlow()

    /** 旧数据兼容：仅承载库里已有的值，保存时原样写回，不再提供修改入口。 */
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()
    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _insertIndex = MutableStateFlow(0)
    val insertIndex: StateFlow<Int> = _insertIndex.asStateFlow()

    /**
     * 只读模式：开启后禁止一切编辑（输入、插入、删除、长按菜单操作）。
     * 仅会话内有效 —— 退出编辑页/重启应用即重置（故用普通 StateFlow，不持久化）。
     */
    private val _readOnly = MutableStateFlow(false)
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()
    fun toggleReadOnly() { _readOnly.value = !_readOnly.value }

    /** 请求把焦点/光标移动到某个块（插入图片/录音后指向其后段文字块）。 */
    private val _focusRequest = MutableStateFlow<Int?>(null)
    val focusRequest: StateFlow<Int?> = _focusRequest.asStateFlow()
    fun consumeFocus(index: Int) { if (_focusRequest.value == index) _focusRequest.value = null }

    init {
        if (noteId == null) {
            _blocks.value = listOf(Block.Text(""))
            _loaded.value = true
        } else viewModelScope.launch {
            repo.getById(noteId)?.let { n ->
                _title.value = n.title
                _blocks.value = BlockOps.ensureTrailingText(
                    BlockSerializer.fromJson(n.body)
                )
                _reminderAt.value = n.reminderAt
                _repeatRule.value = n.repeatRule
                _labels.value = n.labels
                _pinned.value = n.pinned
                // 已完成的一次性提醒：进入笔记后清除「已完成」状态
                if (n.reminderAt != null && n.reminderAt <= System.currentTimeMillis() && n.repeatRule == null) {
                    _reminderAt.value = null
                    repo.updateReminder(noteId, null, null)
                }
            }
            repo.touchLastOpened(noteId)
            _loaded.value = true
        }
    }

    fun updateTitle(t: String) { _title.value = t }
    fun setInsertIndex(i: Int) { _insertIndex.value = i }

    fun updateTextBlock(index: Int, html: String) {
        val list = _blocks.value.toMutableList()
        if (index in list.indices && list[index] is Block.Text) {
            list[index] = Block.Text(html)
            _blocks.value = list
        }
    }

    fun insertBlock(index: Int, block: Block) {
        // 算法在 BlockOps（纯函数，可单测）；此处只负责状态承载
        val list = BlockOps.insertAt(_blocks.value, index, block)
        _blocks.value = list
        _focusRequest.value = list.size - 1
    }

    /**
     * 矢量排布核心：在文字块 index 的光标处插入块。
     * 把该文字块按「光标前 / 光标后」拆成两个文字块，块插入其间 —— 图片/录音因此成为
     * 文档流中的行内原子节点，插入位置来自当前光标，而非固定置顶/置底。
     */
    fun splitAndInsert(index: Int, beforeHtml: String, block: Block, afterHtml: String) {
        val before = _blocks.value
        val list = BlockOps.splitAndInsert(before, index, beforeHtml, block, afterHtml)
        if (list === before) return          // 索引非法/非文字块 → 未变更
        _blocks.value = list
        // 光标移到块后方的文字块，便于回车换行/继续输入
        _focusRequest.value = index + 2
    }

    fun removeBlock(index: Int) {
        val cur = _blocks.value
        // 先取被删块以便清理附件（算法仍交给 BlockOps）
        cur.getOrNull(index)?.let { b ->
            when (b) {
                is Block.Image -> repo.deleteImage(b.path)
                is Block.Audio -> repo.deleteVoice(b.path)
                is Block.File -> repo.deleteFile(b.path)
                else -> {}
            }
        }
        _blocks.value = BlockOps.removeAt(cur, index)
    }

    fun updateReminder(at: Long?, rule: String?) {
        _reminderAt.value = at; _repeatRule.value = rule
    }
    fun updatePinned(v: Boolean) { _pinned.value = v }

    // ---- 附件 ----

    fun resolveImage(name: String): java.io.File = repo.resolveImage(name)
    fun resolveVoice(name: String): java.io.File = repo.resolveVoice(name)
    fun resolveFile(name: String): java.io.File = repo.resolveFile(name)

    /** 导入任意文件（SAF），返回附件目录下的文件名。 */
    suspend fun importAnyFile(uri: Uri): String = repo.importAnyFile(uri)

    /** 重命名文件块（保留扩展名，保持块路径同步）。[baseName] 不含扩展名。 */
    fun renameFile(oldName: String, baseName: String) {
        val newName = repo.renameFile(oldName, baseName)
        if (newName == oldName) return
        val list = _blocks.value.map { b ->
            if (b is Block.File && b.path == oldName) Block.File(newName) else b
        }
        _blocks.value = list
    }
    fun createTempRecordingFile(): java.io.File = repo.createTempRecordingFile()
    fun finalizeRecording(temp: java.io.File, name: String): String = repo.finalizeRecording(temp, name)
    fun renameVoice(oldName: String, newName: String) {
        val new = repo.renameVoiceFile(oldName, newName)
        val list = _blocks.value.map { b -> if (b is Block.Audio && b.path == oldName) Block.Audio(new) else b }
        _blocks.value = list
    }

    /** 复制图片到附件目录，返回文件名（由调用方插入块）。 */
    suspend fun importImage(uri: Uri): String = repo.importImage(uri)

    fun delete() = viewModelScope.launch {
        val id = noteId
        if (id != null && id != 0L) {
            repo.getById(id)?.let { repo.delete(it) }
        } else {
            // 未保存：清理已插入的附件
            for (b in _blocks.value) {
                when (b) {
                    is Block.Image -> repo.deleteImage(b.path)
                    is Block.Audio -> repo.deleteVoice(b.path)
                    is Block.File -> repo.deleteFile(b.path)
                    else -> {}
                }
            }
        }
    }

    suspend fun save(): Long {
        if (!saving.compareAndSet(false, true)) return noteId ?: 0L
        return try {
            repo.saveNote(
                noteId, _title.value, _blocks.value, _reminderAt.value, _repeatRule.value,
                _labels.value, _pinned.value
            )
        } finally {
            saving.set(false)
        }
    }
}
