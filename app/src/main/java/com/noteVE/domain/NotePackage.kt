package com.noteVE.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 导出的笔记数据（不含 id，导入时生成新 id）。 */
data class PackedNote(
    val title: String,
    val blocks: List<Block>,
    val labels: List<String>,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedAt: Long,
    val pinned: Boolean,
    val reminderAt: Long?,
    val repeatRule: String?
)

sealed class PackageData {
    data class Single(val note: PackedNote, val attachments: Map<String, ByteArray>) : PackageData()
    data class Full(val notes: List<PackedNote>, val attachments: Map<String, ByteArray>) : PackageData()
    object Invalid : PackageData()
}

/**
 * 自研导出包格式：gzip 的 tar 容器 + JSON 清单，含完整图片/录音/矢量位置/标题/时间。
 * 仅本应用可解析。
 */
object NotePackage {

    private const val META_JSON = "meta.json"

    // ── 安全上限（防止恶意/损坏包导致 OOM 或写坏文件系统）────────
    /** 单个条目解压上限。 */
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024      // 256 MB
    /** 整包解压总量上限。 */
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024     // 1 GB
    /** 整包条目数上限。 */
    private const val MAX_ENTRIES = 20_000
    /** 附件文件名长度上限（UTF-8 字节）。 */
    private const val MAX_NAME_BYTES = 200

    /**
     * 校验并规范化 tar 条目名。
     *
     * ★ 安全关键：旧实现直接把 `attachments/<name>` 当成文件名交给 `File(dir, name)`，
     *   若包里含 `attachments/../../x` 之类条目即可**写出应用私有目录之外**（路径穿越）。
     *   这里统一拒绝：绝对路径、`..` 段、空名、控制字符、过长名。
     *
     * @return 安全的名字；不安全返回 null（整包视为无效）
     */
    private fun sanitizeEntryName(raw: String): String? {
        if (raw.isBlank()) return null
        // 统一分隔符后按段检查
        val normalized = raw.replace('\\', '/')
        if (normalized.startsWith("/")) return null              // 绝对路径
        val parts = normalized.split('/')
        if (parts.any { it == ".." || it == "." || it.isEmpty() }) return null
        // 控制字符 / 保留字符
        if (normalized.any { it.code < 0x20 || it == '\u007f' }) return null
        if (normalized.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) return null
        return normalized
    }
    private const val NOTE_JSON = "note.json"
    private const val NOTES_JSON = "notes.json"

    fun writeSingle(out: OutputStream, note: PackedNote, attachments: Map<String, ByteArray>) {
        GZIPOutputStream(out).use { gz ->
            Tar.write(gz, META_JSON, """{"format":"noteapp","type":"single","version":1}""".toByteArray())
            Tar.write(gz, NOTE_JSON, noteToJson(note).toString().toByteArray())
            for ((name, bytes) in attachments) Tar.write(gz, "attachments/$name", bytes)
            Tar.finish(gz)
        }
    }

    fun writeFull(out: OutputStream, notes: List<PackedNote>, attachments: Map<String, ByteArray>) {
        GZIPOutputStream(out).use { gz ->
            Tar.write(gz, META_JSON, """{"format":"noteapp","type":"full","version":1}""".toByteArray())
            val arr = JSONArray()
            notes.forEach { arr.put(noteToJson(it)) }
            Tar.write(gz, NOTES_JSON, arr.toString().toByteArray())
            for ((name, bytes) in attachments) Tar.write(gz, "attachments/$name", bytes)
            Tar.finish(gz)
        }
    }

    fun read(input: InputStream): PackageData {
        val entries = try {
            // ★ 限额守卫：超限直接判为无效包，不尝试解压（避免 OOM）
            Tar.read(
                GZIPInputStream(input),
                maxEntries = MAX_ENTRIES,
                maxEntryBytes = MAX_ENTRY_BYTES,
                maxTotalBytes = MAX_TOTAL_BYTES
            )
        } catch (e: Exception) {
            return PackageData.Invalid
        }
        // ★ 所有条目名必须先通过安全检查（阻断路径穿越）
        for (name in entries.keys) {
            if (sanitizeEntryName(name) == null) return PackageData.Invalid
        }
        val meta = entries[META_JSON]?.toString(Charsets.UTF_8)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return PackageData.Invalid
        if (meta.optString("format") != "noteapp") return PackageData.Invalid
        val attachments = LinkedHashMap<String, ByteArray>()
        for ((name, bytes) in entries) {
            if (!name.startsWith("attachments/")) continue
            val rel = name.removePrefix("attachments/")
            // 双重校验：相对名同样不得含路径段或非法字符
            if (sanitizeEntryName(rel) == null) return PackageData.Invalid
            attachments[rel] = bytes
        }
        return when (meta.optString("type")) {
            "single" -> {
                val n = entries[NOTE_JSON]?.let { runCatching { noteFromJson(JSONObject(String(it, Charsets.UTF_8))) }.getOrNull() } ?: return PackageData.Invalid
                PackageData.Single(n, attachments)
            }
            "full" -> {
                val arr = entries[NOTES_JSON]?.let { runCatching { JSONArray(String(it, Charsets.UTF_8)) }.getOrNull() }
                    ?: return PackageData.Invalid
                val notes = mutableListOf<PackedNote>()
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { notes.add(noteFromJson(it)) }
                PackageData.Full(notes, attachments)
            }
            else -> PackageData.Invalid
        }
    }

    private fun noteToJson(n: PackedNote): JSONObject = JSONObject().apply {
        put("title", n.title)
        put("blocks", BlockSerializer.toJson(n.blocks))
        put("labels", JSONArray(n.labels))
        put("createdAt", n.createdAt)
        put("modifiedAt", n.modifiedAt)
        put("lastOpenedAt", n.lastOpenedAt)
        put("pinned", n.pinned)
        put("reminderAt", n.reminderAt ?: JSONObject.NULL)
        put("repeatRule", n.repeatRule ?: JSONObject.NULL)
    }

    private fun noteFromJson(o: JSONObject): PackedNote = PackedNote(
        title = o.optString("title"),
        blocks = BlockSerializer.fromJson(o.optString("blocks")),
        labels = (o.optJSONArray("labels")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i) }
        } ?: emptyList()),
        createdAt = o.optLong("createdAt"),
        modifiedAt = o.optLong("modifiedAt"),
        lastOpenedAt = o.optLong("lastOpenedAt"),
        pinned = o.optBoolean("pinned"),
        reminderAt = if (o.isNull("reminderAt")) null else o.optLong("reminderAt"),
        repeatRule = if (o.isNull("repeatRule")) null else o.optString("repeatRule")
    )
}

/** 极简 tar 写入/读取（512 字节头 + 8 进制字段）。 */
private object Tar {

    fun write(out: OutputStream, name: String, data: ByteArray) {
        val h = ByteArray(512)
        val nb = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nb, 0, h, 0, minOf(nb.size, 100))
        "0000644\u0000".toByteArray().copyInto(h, 100)
        "0000000\u0000".toByteArray().copyInto(h, 108)
        "0000000\u0000".toByteArray().copyInto(h, 116)
        String.format("%011o", data.size).toByteArray().copyInto(h, 124)
        h[135] = ' '.code.toByte()
        "00000000000\u0000".toByteArray().copyInto(h, 136)
        h[156] = '0'.code.toByte()
        for (i in 148..155) h[i] = ' '.code.toByte()
        var sum = 0
        for (b in h) sum += b.toInt() and 0xFF
        String.format("%06o", sum).toByteArray().copyInto(h, 148)
        h[154] = 0
        h[155] = ' '.code.toByte()
        out.write(h)
        out.write(data)
        val pad = (512 - (data.size % 512)) % 512
        if (pad > 0) out.write(ByteArray(pad))
    }

    fun finish(out: OutputStream) {
        out.write(ByteArray(1024))
    }

    /**
     * 读取 tar 流。
     *
     * ★ 三项限额（防止恶意包 OOM/卡死）：
     *   - 条目数上限
     *   - 单条目大小上限（同时避免 `size.toInt()` 溢出为负数）
     *   - 整包累计大小上限
     * 任一超限即抛异常，由调用方转为「无效包」，不会崩溃。
     */
    fun read(
        input: InputStream,
        maxEntries: Int = Int.MAX_VALUE,
        maxEntryBytes: Long = Long.MAX_VALUE,
        maxTotalBytes: Long = Long.MAX_VALUE,
    ): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        val header = ByteArray(512)
        while (true) {
            if (entries.size > maxEntries) throw IllegalStateException("条目数超限")
            var off = 0
            while (off < 512) {
                val r = input.read(header, off, 512 - off)
                if (r < 0) return entries
                off += r
            }
            if (header.all { it == 0.toByte() }) return entries
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000')
            var size = 0L
            for (i in 124 until 136) {
                val c = header[i].toInt().toChar()
                if (c in '0'..'7') size = size * 8 + (c - '0')
            }
            if (size < 0 || size > maxEntryBytes) throw IllegalStateException("条目过大")
            total += size
            if (total > maxTotalBytes) throw IllegalStateException("整包过大")
            if (size > Int.MAX_VALUE) throw IllegalStateException("条目超出可寻址范围")
            val data = ByteArray(size.toInt())
            var dOff = 0
            while (dOff < data.size) {
                val r = input.read(data, dOff, data.size - dOff)
                if (r < 0) return entries
                dOff += r
            }
            if (name.isNotBlank()) entries[name] = data
            val pad = (512 - (data.size % 512)) % 512
            var pOff = 0
            while (pOff < pad) {
                val r = input.read(ByteArray(pad - pOff))
                if (r < 0) return entries
                pOff += r
            }
        }
    }
}
