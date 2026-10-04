package com.noteVE.domain

import android.content.Context
import java.io.File

/**
 * 存储占用统计。
 *
 * 分类（与存储页一致）：**文本 / 图片 / 录音 / 视频 / 音频 / 其它文件**
 * - 文本：正文字符的 UTF-8 字节数（近似，标题 + 纯文本 + 块 JSON）
 * - 图片：`files/attachments/` 中图片块引用的文件
 * - 录音：`files/recordings/` 中的录音文件
 * - 视频 / 音频 / 其它：`files/attachments/` 中任意文件块引用的文件，按扩展名归类
 *
 * 缩略图缓存单独统计（`cacheDir/thumbnails/`），不计入笔记占用 —— 它可被清理。
 */
object StorageStats {

    /** 单个笔记的分类占用（字节）。 */
    data class NoteUsage(
        val noteId: Long,
        val title: String,
        val text: Long,
        val image: Long,
        val voice: Long,
        val video: Long,
        val audio: Long,
        val other: Long,
    ) {
        val total: Long get() = text + image + voice + video + audio + other
    }

    /** 全局汇总。 */
    data class Summary(
        val notes: List<NoteUsage>,
        val cacheBytes: Long,
    ) {
        val text get() = notes.sumOf { it.text }
        val image get() = notes.sumOf { it.image }
        val voice get() = notes.sumOf { it.voice }
        val video get() = notes.sumOf { it.video }
        val audio get() = notes.sumOf { it.audio }
        val other get() = notes.sumOf { it.other }
        val total get() = notes.sumOf { it.total }
    }

    /** 计算全部统计。调用方应在 IO 线程执行。 */
    fun compute(context: Context, notes: List<com.noteVE.data.Note>): Summary {
        val attachDir = File(context.filesDir, "attachments")
        val recordDir = File(context.filesDir, "recordings")

        val list = notes.map { n ->
            var text = n.title.toByteArray().size.toLong() + n.plainText.toByteArray().size.toLong()
            var image = 0L
            var voice = 0L
            var video = 0L
            var audio = 0L
            var other = 0L

            val blocks = BlockSerializer.fromJson(n.body)
            for (b in blocks) {
                when (b) {
                    is Block.Text -> text += b.html.toByteArray().size.toLong()
                    is Block.Image -> image += len(File(attachDir, b.path))
                    is Block.Audio -> voice += len(File(recordDir, b.path))
                    is Block.File -> {
                        val f = File(attachDir, b.path)
                        when (FileKinds.kindOf(b.path)) {
                            FileKinds.Kind.VIDEO -> video += len(f)
                            FileKinds.Kind.AUDIO -> audio += len(f)
                            FileKinds.Kind.DOC -> other += len(f)
                        }
                    }
                }
            }
            NoteUsage(n.id, n.title, text, image, voice, video, audio, other)
        }.sortedByDescending { it.total }   // 按占用从大到小

        return Summary(list, ThumbCache.sizeBytes(context))
    }

    /** 分类展示顺序与配色索引（0..5）。 */
    enum class Category { TEXT, IMAGE, VOICE, VIDEO, AUDIO, OTHER }

    private fun len(f: File): Long = if (f.exists()) f.length() else 0L
}
