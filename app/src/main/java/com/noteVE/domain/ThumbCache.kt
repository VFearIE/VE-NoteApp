package com.noteVE.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 视频缩略图缓存。
 *
 * ## 设计要点
 * - 存放在 `cacheDir/thumbnails/`（**不是** files 目录）——
 *   属于可再生成的派生数据，会被系统/用户「清理缓存」清掉，不占主要存储。
 * - 文件名用「源文件名 + 修改时间 + 目标宽」做哈希键：
 *   源文件变了或尺寸策略变了会自动重新生成，无需手动失效。
 * - 生成失败（编码不支持/损坏）返回 null，由 UI 回退为视频图标。
 */
object ThumbCache {

    private const val DIR = "thumbnails"
    private const val TARGET_W = 320   // 手表屏宽 480，卡片缩略图 320 足够清晰

    private fun dir(context: Context): File =
        File(context.cacheDir, DIR).apply { mkdirs() }

    private fun keyOf(src: File): String {
        val raw = "${src.name}_${src.length()}_${src.lastModified()}_$TARGET_W"
        return raw.hashCode().toUInt().toString(16) + "_${TARGET_W}.jpg"
    }

    /** 取缓存路径（不保证存在）。 */
    fun cacheFileFor(context: Context, src: File): File = File(dir(context), keyOf(src))

    /**
     * 获取（必要时生成）缩略图文件。失败返回 null。
     * 调用方应在 IO 线程调用。
     */
    fun get(context: Context, src: File): File? {
        if (!src.exists()) return null
        val out = cacheFileFor(context, src)
        if (out.exists() && out.length() > 0) return out

        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(src.absolutePath)
                // 取 0.5 秒处的帧（比第 0 帧更可能有画面，避免黑屏开头）
                val frame: Bitmap? = mmr.getFrameAtTime(
                    500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: mmr.frameAtTime
                if (frame == null) return null

                // 等比缩放到目标宽
                val w = TARGET_W
                val h = (frame.height.toFloat() / frame.width * w).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, w, h, true)

                out.outputStream().use { os ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, os)
                }
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                if (out.exists() && out.length() > 0) out else null
            } finally {
                runCatching { mmr.release() }
            }
        } catch (t: Throwable) {
            runCatching { out.delete() }
            null
        }
    }

    /** 读取媒体时长（毫秒）。失败返回 0。调用方应在 IO 线程调用。 */
    fun durationMs(src: File): Long {
        if (!src.exists()) return 0
        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(src.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } finally {
                runCatching { mmr.release() }
            }
        } catch (t: Throwable) {
            0L
        }
    }

    /** 清空全部缩略图缓存。 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /** 当前缓存占用字节数。 */
    fun sizeBytes(context: Context): Long =
        runCatching { dir(context).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)
}
