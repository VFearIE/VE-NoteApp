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
    private const val INDEX_JSON = "attachment-index.json"

    // ── 安全上限（防止恶意/损坏包导致 OOM 或写坏文件系统）────────
    /** 单个条目解压上限。 */
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024      // 256 MB
    /** 整包解压总量上限。 */
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024     // 1 GB
    /** 整包条目数上限。 */
    private const val MAX_ENTRIES = 20_000

    private const val NOTE_JSON = "note.json"
    private const val NOTES_JSON = "notes.json"

    fun writeSingle(out: OutputStream, note: PackedNote, attachments: Map<String, ByteArray>) =
        write(out, listOf(note), attachments, false)

    fun writeFull(out: OutputStream, notes: List<PackedNote>, attachments: Map<String, ByteArray>) =
        write(out, notes, attachments, true)

    /** v2 uses short tar keys; UTF-8 display names live losslessly in the JSON index. */
    private fun write(out: OutputStream, notes: List<PackedNote>, attachments: Map<String, ByteArray>, full: Boolean) {
        val normalized = LinkedHashMap<String, ByteArray>()
        for ((key, bytes) in attachments) {
            val logical = if ('/' in key) AttachmentNames.requireKey(key) else {
                AttachmentNames.requireSafe(key)
                val matches = notes.flatMap { it.blocks }.mapNotNull { AttachmentNames.key(it) }
                    .filter { it.substringAfter('/') == key }.distinct()
                require(matches.size <= 1) { "Ambiguous legacy attachment key: $key" }
                matches.firstOrNull() ?: "attachments/$key"
            }
            require(normalized.put(logical, bytes) == null) { "Duplicate attachment key" }
        }
        for (note in notes) for (block in note.blocks) {
            AttachmentNames.key(block)?.let { require(normalized.containsKey(it)) { "Missing attachment: $it" } }
        }
        val index = JSONObject()
        normalized.keys.forEachIndexed { i, key -> index.put(key, "blobs/$i") }
        val manifest = if (full) JSONArray().apply { notes.forEach { put(noteToJson(it)) } }.toString()
            else noteToJson(notes.single()).toString()
        val entries = linkedMapOf(
            META_JSON to """{"format":"noteapp","type":"${if (full) "full" else "single"}","version":2}""".toByteArray(),
            (if (full) NOTES_JSON else NOTE_JSON) to manifest.toByteArray(Charsets.UTF_8),
            INDEX_JSON to index.toString().toByteArray(Charsets.UTF_8)
        )
        normalized.entries.forEachIndexed { i, entry -> entries["blobs/$i"] = entry.value }
        require(entries.size <= MAX_ENTRIES && entries.values.all { it.size <= MAX_ENTRY_BYTES }) { "Package too large" }
        require(entries.values.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES) { "Package too large" }
        GZIPOutputStream(out).use { gz ->
            entries.forEach { (key, value) -> Tar.write(gz, key, value) }
            Tar.finish(gz)
        }
    }

    fun read(input: InputStream): PackageData = try {
        val entries = GZIPInputStream(input).use {
            Tar.read(it, MAX_ENTRIES, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES)
        }
        val meta = JSONObject(String(entries[META_JSON] ?: error("Missing meta"), Charsets.UTF_8))
        require(meta.getString("format") == "noteapp")
        val version = meta.get("version")
        require(version is Number && (version.toString() == "1" || version.toString() == "2")) { "Unknown version" }
        val full = when (meta.getString("type")) {
            "single" -> false
            "full" -> true
            else -> error("Unknown package type")
        }
        val manifest = String(entries[if (full) NOTES_JSON else NOTE_JSON] ?: error("Missing notes"), Charsets.UTF_8)
        val notes = if (full) {
            val arr = JSONArray(manifest)
            (0 until arr.length()).map { noteFromJson(arr.getJSONObject(it)) }
        } else listOf(noteFromJson(JSONObject(manifest)))
        val attachments = LinkedHashMap<String, ByteArray>()
        val expected = mutableSetOf(META_JSON, if (full) NOTES_JSON else NOTE_JSON)
        if (version.toInt() == 1) {
            for ((name, bytes) in entries) {
                if (name in expected) continue
                require(name.startsWith("attachments/")) { "Unexpected entry" }
                val basename = AttachmentNames.requireSafe(name.removePrefix("attachments/"))
                attachments[basename] = bytes
                expected.add(name)
            }
        } else {
            expected.add(INDEX_JSON)
            val index = JSONObject(String(entries[INDEX_JSON] ?: error("Missing attachment index"), Charsets.UTF_8))
            val used = mutableSetOf<String>()
            for (key in index.keys()) {
                AttachmentNames.requireKey(key)
                val blob = index.getString(key)
                require(blob.matches(Regex("blobs/[0-9]+")) && used.add(blob)) { "Invalid blob key" }
                attachments[key] = entries[blob] ?: error("Missing blob")
                expected.add(blob)
            }
        }
        require(entries.keys == expected) { "Unexpected entries" }
        for (note in notes) for (block in note.blocks) {
            val key = AttachmentNames.key(block) ?: continue
            // v2 must never fall back into the untyped v1 namespace.
            require(attachments.containsKey(if (version.toInt() == 1) key.substringAfter('/') else key)) { "Missing attachment" }
        }
        if (full) PackageData.Full(notes, attachments) else PackageData.Single(notes.single(), attachments)
    } catch (_: Exception) {
        PackageData.Invalid
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

    private fun noteFromJson(o: JSONObject): PackedNote {
        // Do not use the deliberately lenient database decoder for untrusted packages.
        val array = JSONArray(o.getString("blocks"))
        val blocks = (0 until array.length()).map { i ->
            val block = array.getJSONObject(i)
            when (block.getString("type")) {
                "text" -> Block.Text(block.getString("html"))
                "image" -> Block.Image(AttachmentNames.requireSafe(block.getString("path")))
                "audio" -> Block.Audio(AttachmentNames.requireSafe(block.getString("path")))
                "file" -> Block.File(AttachmentNames.requireSafe(block.getString("path")))
                else -> error("Unknown block type")
            }
        }
        val labels = o.getJSONArray("labels")
        return PackedNote(
            title = o.getString("title"), blocks = blocks,
            labels = (0 until labels.length()).map { labels.getString(it) },
            createdAt = o.getLong("createdAt"), modifiedAt = o.getLong("modifiedAt"),
            lastOpenedAt = o.getLong("lastOpenedAt"), pinned = o.getBoolean("pinned"),
            reminderAt = if (o.isNull("reminderAt")) null else o.getLong("reminderAt"),
            repeatRule = if (o.isNull("repeatRule")) null else o.getString("repeatRule")
        )
    }

}

/** 极简 tar 写入/读取（512 字节头 + 8 进制字段）。 */
private object Tar {

    fun write(out: OutputStream, name: String, data: ByteArray) {
        val h = ByteArray(512)
        val nb = name.toByteArray(Charsets.UTF_8)
        require(nb.size <= 100) { "Tar name exceeds 100 UTF-8 bytes" }
        System.arraycopy(nb, 0, h, 0, nb.size)
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
        var count = 0
        fun exact(size: Int): ByteArray {
            val data = ByteArray(size)
            var off = 0
            while (off < size) {
                val n = input.read(data, off, size - off)
                check(n > 0) { "Truncated tar" }
                off += n
            }
            return data
        }
        fun octal(h: ByteArray, offset: Int, length: Int): Long {
            val text = String(h, offset, length, Charsets.US_ASCII).trim(' ', '\u0000')
            require(text.isNotEmpty() && text.all { it in '0'..'7' }) { "Invalid octal field" }
            return text.toLong(8)
        }
        while (true) {
            val header = exact(512)
            if (header.all { it == 0.toByte() }) {
                require(exact(512).all { it == 0.toByte() }) { "Missing tar terminator" }
                // Drain gzip to validate its CRC/trailer, allowing only conventional zero tar padding.
                var padding = 0
                while (true) {
                    val value = input.read()
                    if (value == -1) break
                    require(value == 0 && ++padding <= 1024 * 1024) { "Trailing tar data" }
                }
                return entries
            }
            require(++count <= maxEntries) { "Too many entries" }
            val checksum = octal(header, 148, 8)
            val sum = header.indices.sumOf { if (it in 148..155) 32 else header[it].toInt() and 255 }
            require(checksum == sum.toLong()) { "Invalid tar checksum" }
            require(header[156] == '0'.code.toByte() || header[156] == 0.toByte()) { "Not a regular file" }
            require((157 until 257).all { header[it] == 0.toByte() }) { "Unexpected tar link" }
            require((345 until 500).all { header[it] == 0.toByte() }) { "Unsupported tar prefix" }
            val end = (0 until 100).firstOrNull { header[it] == 0.toByte() } ?: 100
            require((end until 100).all { header[it] == 0.toByte() }) { "Invalid tar name" }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val name = decoder.decode(java.nio.ByteBuffer.wrap(header, 0, end)).toString()
            require(name.isNotBlank() && !entries.containsKey(name)) { "Empty or duplicate entry" }
            val size = octal(header, 124, 12)
            require(size <= maxEntryBytes && size <= Int.MAX_VALUE && size <= maxTotalBytes - total) { "Package too large" }
            total += size
            entries[name] = exact(size.toInt())
            val pad = ((512 - size % 512) % 512).toInt()
            require(exact(pad).all { it == 0.toByte() }) { "Invalid tar padding" }
        }
    }
}
