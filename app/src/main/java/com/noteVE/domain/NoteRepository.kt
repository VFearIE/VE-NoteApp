package com.noteVE.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.noteVE.data.Note
import com.noteVE.data.NoteDao
import com.noteVE.data.NoteDatabase
import com.noteVE.reminder.ReminderScheduler
import com.noteVE.reminder.ReminderService
import com.noteVE.reminder.ReminderTicker
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** 业务仓库：块存储、附件、闹钟、导入导出。 */
class NoteRepository(private val context: Context) {

    private val dao: NoteDao = NoteDatabase.get(context).noteDao()
    private val attachments = AttachmentManager(context)
    private val alarms = ReminderScheduler(context)

    private companion object {
        /** 统一日志标签（本文件同时承担仓库/附件/提醒重排三类职责）。 */
        const val TAG = "NoteAppRepo"

        /** 过期提醒的补发窗口：超过此时长不再补发，避免开机时弹一堆陈年提醒。 */
        const val MISSED_GRACE_MS = 24 * 60 * 60 * 1000L   // 24 小时
    }

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    /** 一次性取全部笔记（存储统计用）。 */
    suspend fun getAllNotes(): List<Note> = dao.getAll()
    fun search(q: String): Flow<List<Note>> = dao.search(q)

    suspend fun getById(id: Long): Note? = dao.getById(id)

    suspend fun getBlocks(id: Long): List<Block> =
        dao.getById(id)?.let { BlockSerializer.fromJson(it.body) } ?: emptyList()

    suspend fun touchLastOpened(id: Long) {
        val n = dao.getById(id) ?: return
        dao.update(n.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    /** 保存笔记（按块模型），返回笔记 id。 */
    suspend fun saveNote(
        id: Long?,
        title: String,
        blocks: List<Block>,
        reminderAt: Long?,
        repeatRule: String?,
        labels: List<String>,
        pinned: Boolean
    ): Long {
        val (images, audios, files) = BlockSerializer.attachmentPaths(blocks)
        val plain = BlockSerializer.toPlainText(blocks)
        val body = BlockSerializer.toJson(blocks)
        val now = System.currentTimeMillis()

        if (id == null || id == 0L) {
            val note = Note(
                title = title, body = body, plainText = plain,
                createdAt = now, modifiedAt = now, lastOpenedAt = now,
                reminderAt = reminderAt, repeatRule = repeatRule,
                imagePaths = images, voiceRecords = audios, filePaths = files,
                labels = labels, pinned = pinned
            )
            val newId = dao.insert(note)
            if (reminderAt != null) alarms.schedule(newId, reminderAt)
            return newId
        } else {
            val old = dao.getById(id) ?: return 0L
            val note = old.copy(
                title = title, body = body, plainText = plain, modifiedAt = now,
                reminderAt = reminderAt, repeatRule = repeatRule,
                imagePaths = images, voiceRecords = audios, filePaths = files,
                labels = labels, pinned = pinned
            )
            dao.update(note)
            if (reminderAt != null && reminderAt > now) alarms.schedule(id, reminderAt)
            else alarms.cancel(id)
            return id
        }
    }

    suspend fun updateReminder(id: Long, at: Long?, rule: String?) {
        val n = dao.getById(id) ?: return
        dao.update(n.copy(reminderAt = at, repeatRule = rule))
        if (at != null && at > System.currentTimeMillis()) alarms.schedule(id, at)
        else alarms.cancel(id)
        // 通知进程内计时器重算（否则要等旧的挂起超时才生效）
        ReminderTicker.refresh()
    }

    /**
     * 删除笔记并清理其附件。
     *
     * ★ 附件清单**从 body（Block 序列）派生**，不读 `imagePaths/voiceRecords/filePaths` 列。
     *   原因：那三列是**派生冗余**（来源是 body）；若某次写入漏同步，读列就会漏删或误删文件。
     *   body 是唯一事实来源，故一切以它为准。
     *   （三列仍保留在表中，仅为旧数据/旧导出包的向后兼容，不再作为业务依据。）
     */
    suspend fun delete(note: Note) {
        alarms.cancel(note.id)
        val (images, audios, files) = BlockSerializer.attachmentPaths(
            BlockSerializer.fromJson(note.body)
        )
        attachments.delete(*images.toTypedArray())
        audios.forEach { deleteVoice(it) }
        files.forEach { deleteFile(it) }
        dao.delete(note)
    }

    suspend fun update(note: Note) = dao.update(note)

    /**
     * 校验并修复「派生列与 body 不一致」的笔记。
     *
     * 背景：`imagePaths / voiceRecords / filePaths` 是从 body 派生的冗余列。
     * 历史上若出现过写入路径不一致（例如重构期间），库内可能残留脏数据。
     * 本方法把三列**重算回与 body 一致**，使旧数据重新自洽。
     *
     * @return 被修复的笔记条数
     */
    suspend fun repairDerivedColumns(): Int {
        var fixed = 0
        for (n in dao.getAll()) {
            val (images, audios, files) = BlockSerializer.attachmentPaths(
                BlockSerializer.fromJson(n.body)
            )
            if (n.imagePaths != images || n.voiceRecords != audios || n.filePaths != files) {
                dao.update(n.copy(imagePaths = images, voiceRecords = audios, filePaths = files))
                fixed++
            }
        }
        return fixed
    }

    // ---- 附件 ----

    suspend fun importImage(uri: Uri): String = attachments.importFromUri(uri)
    fun resolveImage(name: String): File = attachments.resolve(name)
    fun deleteImage(name: String) = attachments.delete(name)

    /** 导入任意文件（SAF），返回附件目录下的文件名。 */
    suspend fun importAnyFile(uri: Uri): String = attachments.importAnyFile(uri)

    /** 任意文件块：与图片同目录（files/attachments/）。 */
    fun resolveFile(name: String): File = attachments.resolve(name)
    fun deleteFile(name: String) { attachments.delete(name) }
    /** 重命名文件块（保留扩展名）。[baseName] 不含扩展名，返回新的完整文件名。 */
    fun renameFile(oldName: String, baseName: String): String = attachments.rename(oldName, baseName)

    private fun recordingsDir(): File = File(context.filesDir, "recordings").apply { mkdirs() }
    fun resolveVoice(name: String): File = File(recordingsDir(), name)
    fun createTempRecordingFile(): File = File(context.filesDir, "recordings/tmp_${System.currentTimeMillis()}.m4a")

    fun finalizeRecording(temp: File, name: String): String {
        val safe = sanitizeName(name)
        val dir = recordingsDir()
        var f = File(dir, "$safe.m4a"); var i = 1
        while (f.exists()) { f = File(dir, "${safe}_$i.m4a"); i++ }
        if (!temp.renameTo(f)) { temp.copyTo(f, overwrite = true); temp.delete() }
        return f.name
    }

    fun deleteVoice(name: String) { resolveVoice(name).delete() }

    fun renameVoiceFile(oldName: String, newName: String): String {
        val old = resolveVoice(oldName)
        if (!old.exists()) return oldName
        val safe = sanitizeName(newName)
        val dir = recordingsDir()
        var f = File(dir, "$safe.m4a"); var i = 1
        while (f.exists() && f.name != oldName) { f = File(dir, "${safe}_$i.m4a"); i++ }
        return if (old.renameTo(f)) f.name else oldName
    }

    private fun sanitizeName(name: String): String =
        name.trim().ifBlank { "recording" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    // ---- 提醒 ----

    /**
     * 重建全部提醒（进程启动 / 开机 / 被拉起时调用）。
     *
     * ★ 关键：不只调度「未来」的提醒，还要**补发刚过期的**。
     *   原因：提醒到点时若进程不在（重启窗口期、被强停等），该条提醒会永久丢失 ——
     *   旧实现用 `reminderAt > now` 过滤，过期的既不补发也不再调度。
     *
     * 补发窗口 [MISSED_GRACE_MS]：过期内的一定时间内，启动时立即触发一次，
     * 让用户不会完全错过提醒；超出窗口的则视为已过时，只清理不再打扰。
     */
    /**
     * 重建全部提醒（**开机 / 进程启动时调用**）。
     *
     * 三档处理：
     *   - 未来      → 正常调度（AlarmManager + 计时器）
     *   - 刚过期≤24h → 立即补发（避免重启窗口期漏掉）
     *   - 过期太久   → 一次性清除；重复提醒续下一轮
     */
    suspend fun rescheduleAllReminders() {
        val now = System.currentTimeMillis()
        var pending = 0

        for (note in dao.getAllWithReminders()) {
            val at = note.reminderAt ?: continue
            when {
                at > now -> {
                    alarms.schedule(note.id, at)
                    pending++
                }
                now - at <= MISSED_GRACE_MS -> {
                    Log.i(TAG, "补发过期提醒 note=${note.id} 过期=${(now - at) / 1000}s")
                    // 标题由服务自查，这里只传 id
                    val svc = Intent(context, ReminderService::class.java).apply {
                        putExtra(ReminderService.EXTRA_NOTE_ID, note.id)
                    }
                    runCatching { ContextCompat.startForegroundService(context, svc) }
                        .onFailure { Log.w(TAG, "补发启动服务失败: $it") }
                    pending++
                }
                else -> {
                    val next = nextRepeat(at, note.repeatRule)
                    if (next != null && next > now) {
                        updateReminder(note.id, next, note.repeatRule)
                        pending++
                    } else {
                        updateReminder(note.id, null, null)
                    }
                }
            }
        }

        Log.i(TAG, "提醒重建完成，待办 $pending 条")
        ReminderTicker.refresh()   // 让计时器立即生效
    }

    suspend fun getNoteForReminder(id: Long): Note? = dao.getById(id)

    /** 全部带提醒的笔记快照。 */
    suspend fun remindersSnapshot(): List<Note> = dao.getAllWithReminders()

    /** 下一条待办提醒（计时器用）。 */
    suspend fun nextDueReminder(): Note? = dao.nextDueReminder()
    fun nextRepeat(triggerAt: Long, rule: String?): Long? = RepeatRule.next(triggerAt, rule)

    // ---- 导出 ----

    private suspend fun readAttachments(blocks: List<Block>): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        for (b in blocks) {
            val name = when (b) {
                is Block.Image -> b.path
                is Block.Audio -> b.path
                is Block.File -> b.path
                else -> null
            } ?: continue
            val f = when (b) {
                is Block.Image -> resolveImage(name)
                is Block.File -> resolveFile(name)
                else -> resolveVoice(name)
            }
            if (f.exists()) map[name] = f.readBytes()
        }
        return map
    }

    suspend fun exportSingle(id: Long, out: OutputStream): Boolean {
        val note = dao.getById(id) ?: return false
        val blocks = BlockSerializer.fromJson(note.body)
        val packed = PackedNote(
            note.title, blocks, note.labels, note.createdAt, note.modifiedAt,
            note.lastOpenedAt, note.pinned, note.reminderAt, note.repeatRule
        )
        NotePackage.writeSingle(out, packed, readAttachments(blocks))
        return true
    }

    suspend fun exportFull(out: OutputStream) {
        val notes = dao.getAll()
        val packed = notes.map { n ->
            val blocks = BlockSerializer.fromJson(n.body)
            PackedNote(n.title, blocks, n.labels, n.createdAt, n.modifiedAt, n.lastOpenedAt, n.pinned, n.reminderAt, n.repeatRule)
        }
        val allBlocks = notes.flatMap { BlockSerializer.fromJson(it.body) }
        NotePackage.writeFull(out, packed, readAttachments(allBlocks))
    }

    // ---- 导入 ----

    sealed class ImportResult {
        /** 统一导入结果：added=本次新增条数，skipped=因重复跳过的条数。 */
        data class Imported(val added: Int, val skipped: Int) : ImportResult()
        object Invalid : ImportResult()
    }

    /**
     * 统一导入：自动识别单备份包 / 全量备份包，不再需要用户区分入口。
     * 规则：仅新增，按 title+createdAt 去重，不覆盖已有笔记。
     */
    suspend fun importAuto(input: InputStream): ImportResult {
        return when (val d = NotePackage.read(input)) {
            is PackageData.Single -> {
                if (dao.findExisting(d.note.title, d.note.createdAt) != null) {
                    ImportResult.Imported(0, 1)
                } else {
                    insertPacked(d.note, d.attachments)
                    ImportResult.Imported(1, 0)
                }
            }
            is PackageData.Full -> {
                var added = 0; var skipped = 0
                for (p in d.notes) {
                    if (dao.findExisting(p.title, p.createdAt) != null) { skipped++; continue }
                    insertPacked(p, d.attachments)
                    added++
                }
                ImportResult.Imported(added, skipped)
            }
            else -> ImportResult.Invalid
        }
    }

    /** 目标文件是否位于 [dir] 之内（用规范化路径比较，可识别 `..` 与软链跳转）。 */
    private fun isInside(target: java.io.File, dir: java.io.File): Boolean = try {
        val base = dir.canonicalFile
        val t = target.canonicalFile
        t.path == base.path || t.path.startsWith(base.path + java.io.File.separator)
    } catch (t: Throwable) {
        false
    }

    private suspend fun insertPacked(p: PackedNote, attachments: Map<String, ByteArray>): Long {
        for (b in p.blocks) {
            val name = when (b) {
                is Block.Image -> b.path
                is Block.Audio -> b.path
                is Block.File -> b.path
                else -> null
            } ?: continue
            val bytes = attachments[name] ?: continue
            val dest = when (b) {
                is Block.Image -> resolveImage(name)
                is Block.File -> resolveFile(name)
                else -> resolveVoice(name)
            }
            // ★ 纵深防御：即便上游已校验过包内条目名，这里仍确认最终写入路径
            //   确实位于预期目录之内 —— 阻断任何形式的路径穿越（../、符号链接等）。
            // ★ 注意：本方法的参数名为 `attachments`（包内的附件字节表），
            //   它会遮蔽类字段 `attachments`（AttachmentManager）——
            //   故这里必须用 `this.attachments` 才能取到附件目录。
            if (!isInside(dest, this.attachments.attachmentDir) &&
                !isInside(dest, File(context.filesDir, "recordings"))
            ) {
                Log.w(TAG, "拒绝写出目录之外的附件: ${dest.path}")
                continue
            }
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
        }
        val (images, audios, files) = BlockSerializer.attachmentPaths(p.blocks)
        val note = Note(
            title = p.title, body = BlockSerializer.toJson(p.blocks),
            plainText = BlockSerializer.toPlainText(p.blocks),
            createdAt = p.createdAt, modifiedAt = p.modifiedAt, lastOpenedAt = p.lastOpenedAt,
            pinned = p.pinned, reminderAt = p.reminderAt, repeatRule = p.repeatRule,
            imagePaths = images, voiceRecords = audios, filePaths = files, labels = p.labels
        )
        return dao.insert(note)
    }
}
