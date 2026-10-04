package com.noteVE.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑器核心逻辑（矢量排布）单元测试。
 *
 * 覆盖 v2.1.0 要求的关键场景：
 * 光标在开头/中间/末尾插入、空文字块、连续插入、删除、末尾文字块不变式。
 */
class BlockOpsTest {

    private val t = { s: String -> Block.Text(s) }

    // ── splitAndInsert：光标位置插入 ───────────────────────────

    @Test
    fun `光标在开头插入附件`() {
        val blocks = listOf(t("世界"))
        // 光标在 0：前段为空，后段是原文
        val out = BlockOps.splitAndInsert(blocks, 0, "", Block.Image("a.jpg"), "世界")

        assertEquals(3, out.size)
        assertEquals(Block.Text(""), out[0])
        assertEquals(Block.Image("a.jpg"), out[1])
        assertEquals(Block.Text("世界"), out[2])
    }

    @Test
    fun `光标在中间插入附件`() {
        val blocks = listOf(t("你好世界"))
        val out = BlockOps.splitAndInsert(blocks, 0, "你好", Block.Image("a.jpg"), "世界")

        assertEquals(3, out.size)
        assertEquals(Block.Text("你好"), out[0])
        assertEquals(Block.Image("a.jpg"), out[1])
        assertEquals(Block.Text("世界"), out[2])
    }

    @Test
    fun `光标在末尾插入附件`() {
        val blocks = listOf(t("你好"))
        val out = BlockOps.splitAndInsert(blocks, 0, "你好", Block.Image("a.jpg"), "")

        assertEquals(3, out.size)
        assertEquals(Block.Text("你好"), out[0])
        assertEquals(Block.Image("a.jpg"), out[1])
        assertEquals(Block.Text(""), out[2])
    }

    @Test
    fun `对空文字块插入`() {
        val blocks = listOf(t(""))
        val out = BlockOps.splitAndInsert(blocks, 0, "", Block.Audio("r.m4a"), "")

        assertEquals(3, out.size)
        assertEquals(Block.Audio("r.m4a"), out[1])
        assertEquals(Block.Text(""), out[0])
        assertEquals(Block.Text(""), out[2])
    }

    @Test
    fun `索引越界或非文字块时原样返回`() {
        val blocks = listOf(t("a"), Block.Image("i.jpg"), t("b"))

        // 越界
        assertSame(blocks, BlockOps.splitAndInsert(blocks, 99, "", Block.Image("x"), ""))
        assertSame(blocks, BlockOps.splitAndInsert(blocks, -1, "", Block.Image("x"), ""))
        // 目标不是文字块
        assertSame(blocks, BlockOps.splitAndInsert(blocks, 1, "", Block.Image("x"), ""))
    }

    @Test
    fun `连续插入多个附件`() {
        var blocks = listOf(t("abc"))

        // 在 "abc" 中间插图片 → a | img | bc
        blocks = BlockOps.splitAndInsert(blocks, 0, "a", Block.Image("1.jpg"), "bc")
        assertEquals(3, blocks.size)

        // 再在末段 "bc" 中间插录音 → a | img | b | aud | c
        blocks = BlockOps.splitAndInsert(blocks, 2, "b", Block.Audio("2.m4a"), "c")
        assertEquals(5, blocks.size)
        assertEquals(Block.Image("1.jpg"), blocks[1])
        assertEquals(Block.Audio("2.m4a"), blocks[3])
    }

    // ── insertAt：无光标的兜底路径 ─────────────────────────────

    @Test
    fun `末尾无文字块时自动补一个`() {
        val blocks = listOf(t("x"))
        val out = BlockOps.insertAt(blocks, 1, Block.Image("a.jpg"))

        assertEquals(3, out.size)
        assertTrue("末尾必须是文字块", out.last() is Block.Text)
    }

    @Test
    fun `索引越界时插入到末尾`() {
        val blocks = listOf(t("x"))
        val out = BlockOps.insertAt(blocks, 99, Block.Image("a.jpg"))
        assertEquals(3, out.size)
        assertEquals(Block.Image("a.jpg"), out[1])
    }

    @Test
    fun `插入到开头`() {
        val blocks = listOf(t("x"))
        val out = BlockOps.insertAt(blocks, 0, Block.Image("a.jpg"))
        assertEquals(Block.Image("a.jpg"), out[0])
        assertEquals(Block.Text("x"), out[1])
    }

    // ── removeAt ───────────────────────────────────────────────

    @Test
    fun `删除中间块`() {
        val blocks = listOf(t("a"), Block.Image("i.jpg"), t("b"))
        val out = BlockOps.removeAt(blocks, 1)

        assertEquals(2, out.size)
        assertEquals(Block.Text("a"), out[0])
        assertEquals(Block.Text("b"), out[1])
    }

    @Test
    fun `删空后补一个空文字块`() {
        val out = BlockOps.removeAt(listOf(t("only")), 0)

        assertEquals(1, out.size)
        assertEquals(Block.Text(""), out[0])
    }

    @Test
    fun `删除越界索引不改变序列`() {
        val blocks = listOf(t("a"), t("b"))
        val out = BlockOps.removeAt(blocks, 99)
        assertEquals(blocks, out)
    }

    // ── ensureTrailingText ─────────────────────────────────────

    @Test
    fun `空序列规范化为单个空文字块`() {
        assertEquals(listOf(Block.Text("")), BlockOps.ensureTrailingText(emptyList()))
    }

    @Test
    fun `末尾是附件时补文字块`() {
        val out = BlockOps.ensureTrailingText(listOf(t("a"), Block.Image("i.jpg")))
        assertEquals(3, out.size)
        assertTrue(out.last() is Block.Text)
    }

    @Test
    fun `末尾已是文字块时不重复添加`() {
        val blocks = listOf(t("a"), Block.Image("i.jpg"), t("b"))
        assertEquals(blocks, BlockOps.ensureTrailingText(blocks))
    }
}
