package com.noteVE.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件类型识别与格式化测试。
 *
 * 这些是「任意文件块」按扩展名分流（视频 / 音频 / 文档）的依据，
 * 以及存储页与详情弹窗的体积/时长显示格式。
 */
class FileKindsTest {

    // ── 扩展名提取 ─────────────────────────────────────────────

    @Test
    fun `扩展名提取且统一小写`() {
        assertEquals("jpg", FileKinds.extOf("a.JPG"))
        assertEquals("mp4", FileKinds.extOf("视频.MP4"))
        assertEquals("m4a", FileKinds.extOf("rec.m4a"))
    }

    @Test
    fun `无扩展名返回空串`() {
        assertEquals("", FileKinds.extOf("README"))
        assertEquals("", FileKinds.extOf(""))
    }

    @Test
    fun `多点文件名取最后一个扩展名`() {
        assertEquals("gz", FileKinds.extOf("archive.tar.gz"))
        assertEquals("png", FileKinds.extOf("我的.照片.2026.png"))
    }

    // ── 大类判定 ───────────────────────────────────────────────

    @Test
    fun `视频扩展名识别`() {
        for (e in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts")) {
            assertEquals("$e 应识别为视频", FileKinds.Kind.VIDEO, FileKinds.kindOf("f.$e"))
            assertTrue(FileKinds.isVideo("f.$e"))
        }
    }

    @Test
    fun `音频扩展名识别`() {
        for (e in listOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr")) {
            assertEquals("$e 应识别为音频", FileKinds.Kind.AUDIO, FileKinds.kindOf("f.$e"))
            assertTrue(FileKinds.isAudio("f.$e"))
        }
    }

    @Test
    fun `其它一律归为文档`() {
        for (e in listOf("pdf", "txt", "zip", "docx", "apk", "bin", "unknown")) {
            assertEquals("$e 应归为文档", FileKinds.Kind.DOC, FileKinds.kindOf("f.$e"))
        }
        // 无扩展名同样归为文档
        assertEquals(FileKinds.Kind.DOC, FileKinds.kindOf("README"))
    }

    @Test
    fun `图片类别互斥判定`() {
        assertTrue(FileKinds.isImage("a.png"))
        assertTrue(FileKinds.isImage("a.JPEG"))
        assertTrue(FileKinds.isImage("a.webp"))
        // 图片不属于视频/音频
        assertFalse(FileKinds.isVideo("a.png"))
        assertFalse(FileKinds.isAudio("a.png"))
    }

    @Test
    fun `判定对大小写不敏感`() {
        assertEquals(FileKinds.Kind.VIDEO, FileKinds.kindOf("MOVIE.MP4"))
        assertEquals(FileKinds.Kind.AUDIO, FileKinds.kindOf("SONG.MP3"))
    }

    // ── 体积格式化 ─────────────────────────────────────────────

    @Test
    fun `字节级格式化`() {
        assertEquals("0 B", FileKinds.formatSize(0))
        assertEquals("512 B", FileKinds.formatSize(512))
        assertEquals("1023 B", FileKinds.formatSize(1023))
    }

    @Test
    fun `KB 与 MB 格式化保留两位有效小数`() {
        assertEquals("1.00 KB", FileKinds.formatSize(1024))
        assertEquals("1.00 MB", FileKinds.formatSize(1024L * 1024))
        // 2.57 MB
        assertEquals("2.57 MB", FileKinds.formatSize((2.57 * 1024 * 1024).toLong()))
    }

    @Test
    fun `大数值降低精度以免过长`() {
        // >=100 时取整
        assertTrue(FileKinds.formatSize((150 * 1024 * 1024).toLong()).startsWith("150"))
        // >=10 时一位小数
        assertTrue(FileKinds.formatSize((15 * 1024 * 1024).toLong()).startsWith("15."))
    }

    @Test
    fun `GB 换算`() {
        // Android 的 Long.MAX_VALUE 也应正常显示为 GB 而不溢出
        val s = FileKinds.formatSize(Long.MAX_VALUE)
        assertTrue("应为 GB 量级: $s", s.endsWith("GB"))
    }

    @Test
    fun `负数不崩溃`() {
        assertEquals("-", FileKinds.formatSize(-1))
    }

    // ── 时长格式化 ─────────────────────────────────────────────

    @Test
    fun `时长 mm ss`() {
        assertEquals("00:00", FileKinds.formatDuration(0))
        assertEquals("00:05", FileKinds.formatDuration(5_000))
        assertEquals("01:30", FileKinds.formatDuration(90_000))
        assertEquals("59:59", FileKinds.formatDuration(3_599_000))
    }

    @Test
    fun `时长超过一小时带小时位`() {
        assertEquals("1:00:00", FileKinds.formatDuration(3_600_000))
        assertEquals("2:30:15", FileKinds.formatDuration((2 * 3600 + 30 * 60 + 15) * 1000L))
    }

    @Test
    fun `负时长显示为零`() {
        assertEquals("00:00", FileKinds.formatDuration(-1000))
    }
}
