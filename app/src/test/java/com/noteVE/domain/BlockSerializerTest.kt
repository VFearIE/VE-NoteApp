package com.noteVE.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 块序列序列化与附件提取测试。
 *
 * 说明：`toPlainText()` 依赖 Android 的 `Html.fromHtml`，纯 JVM 测试无法覆盖，
 * 故本类只测 **JSON 往返** 与 **附件路径提取**（均为纯逻辑）。
 * 纯文本提取由真机 / Instrumentation 测试覆盖。
 */
class BlockSerializerTest {

    // ── JSON 往返 ──────────────────────────────────────────────

    @Test
    fun `文字块往返`() {
        val src = listOf(Block.Text("<b>加粗</b>普通"))
        val out = BlockSerializer.fromJson(BlockSerializer.toJson(src))
        assertEquals(src, out)
    }

    @Test
    fun `四类块混合往返`() {
        val src = listOf(
            Block.Text("开头"),
            Block.Image("pic.png"),
            Block.Audio("rec.m4a"),
            Block.File("video.mp4"),
            Block.Text("结尾"),
        )
        val out = BlockSerializer.fromJson(BlockSerializer.toJson(src))
        assertEquals(src, out)
    }

    @Test
    fun `空序列往返`() {
        assertEquals(emptyList<Block>(), BlockSerializer.fromJson(BlockSerializer.toJson(emptyList())))
    }

    @Test
    fun `非法 JSON 返回空序列而不是抛异常`() {
        assertEquals(emptyList<Block>(), BlockSerializer.fromJson("这不是 JSON"))
        assertEquals(emptyList<Block>(), BlockSerializer.fromJson(""))
        assertEquals(emptyList<Block>(), BlockSerializer.fromJson("{}"))
    }

    @Test
    fun `未知 type 退化为文字块`() {
        val json = """[{"type":"unknown","path":"x"}]"""
        val out = BlockSerializer.fromJson(json)
        assertEquals(1, out.size)
        assertTrue("未知类型应退化为文字块以保证不丢内容", out[0] is Block.Text)
    }

    @Test
    fun `中文与特殊字符在往返中保持`() {
        val src = listOf(
            Block.Text("标题：中文，emoji 🙂，符号 <> & \" '"),
            Block.File("中文文件名 空格.tar.gz"),
        )
        val out = BlockSerializer.fromJson(BlockSerializer.toJson(src))
        assertEquals(src, out)
    }

    // ── 附件路径提取 ───────────────────────────────────────────

    @Test
    fun `提取三类附件路径`() {
        val blocks = listOf(
            Block.Text("a"),
            Block.Image("i1.png"),
            Block.Image("i2.jpg"),
            Block.Audio("r1.m4a"),
            Block.File("v1.mp4"),
            Block.File("d1.pdf"),
        )
        val (images, audios, files) = BlockSerializer.attachmentPaths(blocks)

        assertEquals(listOf("i1.png", "i2.jpg"), images)
        assertEquals(listOf("r1.m4a"), audios)
        assertEquals(listOf("v1.mp4", "d1.pdf"), files)
    }

    @Test
    fun `无附件时三个列表均为空`() {
        val (i, a, f) = BlockSerializer.attachmentPaths(listOf(Block.Text("只有文字")))
        assertTrue(i.isEmpty() && a.isEmpty() && f.isEmpty())
    }

    @Test
    fun `附件顺序与块顺序一致`() {
        val blocks = listOf(
            Block.Image("z.png"),
            Block.Text("x"),
            Block.Image("a.png"),
        )
        val (images, _, _) = BlockSerializer.attachmentPaths(blocks)
        assertEquals(listOf("z.png", "a.png"), images)
    }

    @Test
    fun `从序列化结果提取的附件与直接从块提取一致`() {
        val blocks = listOf(Block.Text("t"), Block.Image("i.png"), Block.File("f.bin"))
        val fromJson = BlockSerializer.fromJson(BlockSerializer.toJson(blocks))

        assertEquals(
            BlockSerializer.attachmentPaths(blocks),
            BlockSerializer.attachmentPaths(fromJson)
        )
    }
}
