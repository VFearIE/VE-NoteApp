package com.noteVE.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 附件管理：图片、录音、任意文件统一存于私有目录 `files/attachments/`，
 * 数据库只存相对文件名（禁止 BLOB）。
 */
class AttachmentManager(private val context: Context) {

    val attachmentDir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun resolve(relativeName: String): File = File(attachmentDir, relativeName)

    /** 从相册 URI 拷贝图片。**保留原文件名**（缺失时才用时间戳兜底）。 */
    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val ext = imageExtension(uri) ?: "jpg"
        copyIn(uri, uniqueName(baseNameOf(uri, "IMG"), ext))
    }

    /**
     * 从任意来源 URI 导入文件（SAF 选择器）。
     * 扩展名优先取 URI 显示名的后缀，其次 MIME，最后回退 `bin`。
     * 返回保存后的相对文件名。
     */
    suspend fun importAnyFile(uri: Uri): String = withContext(Dispatchers.IO) {
        val ext = extensionOf(uri)
        copyIn(uri, uniqueName(baseNameOf(uri, "FILE"), ext))
    }

    /**
     * 原文件名主干（不含扩展名）。
     * ★ 必须保留：此前用 UUID 命名导致用户看到的全是随机名，无法辨认。
     */
    private fun baseNameOf(uri: Uri, fallbackPrefix: String): String {
        val dn = displayName(uri)
        val raw = if (!dn.isNullOrBlank()) {
            if (dn.contains('.')) dn.substringBeforeLast('.') else dn
        } else {
            "${fallbackPrefix}_${System.currentTimeMillis()}"
        }
        val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|\n\r\t]"), "_")
        return cleaned.ifBlank { "${fallbackPrefix}_${System.currentTimeMillis()}" }.take(60)
    }

    /** 同名时加 `_1`、`_2` 后缀，避免覆盖。 */
    private fun uniqueName(base: String, ext: String): String {
        fun of(n: String) = if (ext.isBlank()) "$n" else "$n.$ext"
        var f = File(attachmentDir, of(base))
        var i = 1
        while (f.exists()) { f = File(attachmentDir, of("${base}_$i")); i++ }
        return f.name
    }

    private fun copyIn(uri: Uri, name: String): String {
        val dest = File(attachmentDir, name)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException(context.getString(com.noteVE.R.string.err_cannot_read_image))
        input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
        return name
    }

    /** 查询 URI 显示名（用于取扩展名）。 */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 任意文件：从显示名取扩展名，再退到 MIME 映射，最后 `bin`。 */
    private fun extensionOf(uri: Uri): String {
        displayName(uri)?.let { n ->
            val e = n.substringAfterLast('.', "")
            if (e.isNotBlank() && e.length <= 8 && e.all { it.isLetterOrDigit() }) return e.lowercase()
        }
        val mime = context.contentResolver.getType(uri)
        return mimeToExt(mime) ?: "bin"
    }

    private fun imageExtension(uri: Uri): String? = when (context.contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> null
    }

    private fun mimeToExt(mime: String?): String? {
        if (mime.isNullOrBlank()) return null
        val sub = mime.substringAfter('/', "")
        return when {
            sub.startsWith("vnd.") -> "bin"
            sub.isBlank() -> null
            sub.contains("+") -> sub.substringBefore('+')
            else -> sub
        }
    }

    fun delete(vararg names: String) {
        names.forEach { n ->
            if (n.isNotBlank()) File(attachmentDir, n).delete()
        }
    }

    /**
     * 重命名附件（同目录，**保留原扩展名**）。
     * [baseName] 为不含扩展名的新名称；返回新的完整文件名（失败则原样返回）。
     */
    fun rename(oldName: String, baseName: String): String {
        if (oldName.isBlank() || baseName.isBlank()) return oldName
        val src = File(attachmentDir, oldName)
        if (!src.exists()) return oldName
        val ext = oldName.substringAfterLast('.', "")
        val safe = sanitize(baseName)
        fun target(n: String) =
            if (ext.isBlank()) File(attachmentDir, n) else File(attachmentDir, "$n.$ext")
        var dst = target(safe)
        var i = 1
        while (dst.exists() && dst.name != oldName) { dst = target("${safe}_$i"); i++ }
        return if (src.renameTo(dst)) dst.name else oldName
    }

    private fun sanitize(name: String): String {
        val t = name.trim().replace(Regex("[/\\:*?\"<>|]"), "_")
        return t.ifBlank { "file" }.take(80)
    }
}
