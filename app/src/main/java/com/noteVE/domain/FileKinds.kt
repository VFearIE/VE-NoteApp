package com.noteVE.domain

/**
 * 文件类型识别与格式化工具。
 *
 * 用于「任意文件块」：
 *  - 按**扩展名**判定类别（视频 / 音频 / 其它文档）
 *  - 人类可读的体积格式化
 *  - 时长读取（音频/视频）
 */
object FileKinds {

    /** 文件大类。 */
    enum class Kind { VIDEO, AUDIO, DOC }

    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "m4v", "ts", "mpeg", "mpg", "rmvb"
    )
    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma", "ape", "mid", "midi"
    )
    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )

    /** 取小写扩展名（不含点）。 */
    fun extOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    /** 判定大类。 */
    fun kindOf(name: String): Kind = when (extOf(name)) {
        in VIDEO_EXT -> Kind.VIDEO
        in AUDIO_EXT -> Kind.AUDIO
        else -> Kind.DOC
    }

    /** 是否为图片类（插入时若为图片，仍走图片通道）。 */
    fun isImage(name: String): Boolean = extOf(name) in IMAGE_EXT

    /** 是否为音频（含录音用的 m4a；插入时走音频通道）。 */
    fun isAudio(name: String): Boolean = extOf(name) in AUDIO_EXT

    /** 是否为视频。 */
    fun isVideo(name: String): Boolean = extOf(name) in VIDEO_EXT

    /**
     * 体积格式化，保留两位有效小数。
     * 例：`2.57 MB` / `812.4 KB` / `931 B`
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return fmt(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024) return fmt(mb) + " MB"
        return fmt(mb / 1024.0) + " GB"
    }

    private fun fmt(v: Double): String =
        if (v >= 100) String.format("%.0f", v)
        else if (v >= 10) String.format("%.1f", v)
        else String.format("%.2f", v)

    /** 时长格式化 `mm:ss` / `h:mm:ss`。 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
