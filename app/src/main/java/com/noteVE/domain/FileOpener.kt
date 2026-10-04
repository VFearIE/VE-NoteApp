package com.noteVE.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 「以其他方式打开」：把私有目录里的文件通过 [FileProvider] 交给外部应用。
 *
 * 用途：图片/录音除应用内查看外，还能：
 *   - 用系统图库/播放器打开
 *   - 通过文件管理器复制导出
 *   - 分享到其它应用
 *
 * 依赖 manifest 中的 FileProvider 声明与 `res/xml/file_paths.xml`。
 * 只暴露 `files/attachments/` 与 `files/recordings/`，不触碰数据库等私有数据。
 */
object FileOpener {

    /** 根据扩展名推断 MIME。 */
    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        else -> "*/*"
    }

    /**
     * 以「打开方式」选择器打开文件。
     * @param subDir "attachments" 或 "recordings"
     */
    fun openWith(context: Context, subDir: String, fileName: String) {
        val file = File(File(context.filesDir, subDir), fileName)
        if (!file.exists()) return
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 若无任何应用可打开，给个兜底提示（不崩溃）
        runCatching { context.startActivity(chooser) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = mimeOf(fileName)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.let { Intent.createChooser(it, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            }
    }

    const val DIR_IMAGES = "attachments"
    const val DIR_RECORDINGS = "recordings"
}
