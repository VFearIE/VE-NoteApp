package com.noteVE.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 导入导出包格式测试。
 *
 * 重点覆盖 v2.1.0 修复的**安全风险**：
 * - 路径穿越（`../` 构造）必须被拒绝
 * - 超大条目 / 超量条目必须被拒绝且不崩溃
 * 以及格式往返的一致性（含中文名、多附件、空内容）。
 */
class NotePackageTest {

    private fun note(
        title: String = "测试笔记",
        blocks: List<Block> = listOf(Block.Text("正文")),
    ) = PackedNote(
        title = title,
        blocks = blocks,
        labels = emptyList(),
        createdAt = 1_700_000_000_000L,
        modifiedAt = 1_700_000_001_000L,
        lastOpenedAt = 1_700_000_002_000L,
        pinned = true,
        reminderAt = 1_700_000_003_000L,
        repeatRule = RepeatRule.DAILY,
    )

    private fun writeSingleBytes(
        n: PackedNote,
        attachments: Map<String, ByteArray>,
    ): ByteArray = ByteArrayOutputStream().also { NotePackage.writeSingle(it, n, attachments) }.toByteArray()

    private fun writeFullBytes(
        notes: List<PackedNote>,
        attachments: Map<String, ByteArray>,
    ): ByteArray = ByteArrayOutputStream().also { NotePackage.writeFull(it, notes, attachments) }.toByteArray()

    private fun read(data: ByteArray): PackageData = NotePackage.read(ByteArrayInputStream(data))

    // ── 往返一致性 ─────────────────────────────────────────────

    @Test
    fun `单条笔记往返 - 无附件`() {
        val src = note()
        val data = read(writeSingleBytes(src, emptyMap()))

        assertTrue(data is PackageData.Single)
        val got = (data as PackageData.Single).note
        assertEquals(src.title, got.title)
        assertEquals(src.blocks, got.blocks)
        assertEquals(src.createdAt, got.createdAt)
        assertEquals(src.modifiedAt, got.modifiedAt)
        assertEquals(src.lastOpenedAt, got.lastOpenedAt)
        assertEquals(src.pinned, got.pinned)
        assertEquals(src.reminderAt, got.reminderAt)
        assertEquals(src.repeatRule, got.repeatRule)
    }

    @Test
    fun `单条笔记往返 - 含中文名附件`() {
        val src = note(blocks = listOf(Block.Text("x"), Block.Image("我的照片.png")))
        val att = mapOf("我的照片.png" to byteArrayOf(1, 2, 3))

        val data = read(writeSingleBytes(src, att)) as PackageData.Single
        assertEquals(byteArrayOf(1, 2, 3).toList(), data.attachments["我的照片.png"]?.toList())
    }

    @Test
    fun `单条笔记往返 - 多类型附件`() {
        val src = note(
            blocks = listOf(
                Block.Text("a"),
                Block.Image("i.png"),
                Block.Audio("r.m4a"),
                Block.File("v.mp4"),
            )
        )
        val att = mapOf(
            "i.png" to byteArrayOf(1),
            "r.m4a" to byteArrayOf(2),
            "v.mp4" to byteArrayOf(3),
        )
        val data = read(writeSingleBytes(src, att)) as PackageData.Single

        assertEquals(3, data.attachments.size)
        assertEquals(src.blocks, data.note.blocks)
    }

    @Test
    fun `全量导出往返`() {
        val notes = listOf(note("一"), note("二"), note("三"))
        val data = read(writeFullBytes(notes, mapOf("a.jpg" to byteArrayOf(9)))) as PackageData.Full

        assertEquals(3, data.notes.size)
        assertEquals(listOf("一", "二", "三"), data.notes.map { it.title })
        assertTrue(data.attachments.containsKey("a.jpg"))
    }

    @Test
    fun `空笔记列表的全量导出`() {
        val data = read(writeFullBytes(emptyList(), emptyMap()))
        assertTrue("空列表也应产出合法包", data is PackageData.Full)
        assertTrue((data as PackageData.Full).notes.isEmpty())
    }

    @Test
    fun `空附件内容`() {
        val src = note(blocks = listOf(Block.Text("t"), Block.File("empty.bin")))
        val data = read(writeSingleBytes(src, mapOf("empty.bin" to ByteArray(0)))) as PackageData.Single
        assertNotNull(data.attachments["empty.bin"])
        assertEquals(0, data.attachments["empty.bin"]!!.size)
    }

    @Test
    fun `提醒字段为 null 时往返保持 null`() {
        val src = note().copy(reminderAt = null, repeatRule = null)
        val got = (read(writeSingleBytes(src, emptyMap())) as PackageData.Single).note
        assertEquals(null, got.reminderAt)
        assertEquals(null, got.repeatRule)
    }

    // ── 安全：路径穿越 ─────────────────────────────────────────

    @Test
    fun `拒绝含父目录段的附件条目`() {
        val src = note(blocks = listOf(Block.Text("t"), Block.File("../../evil.txt")))
        // 故意用恶意名字写出，再读回 —— 必须被判为无效包
        val data = read(writeSingleBytes(src, mapOf("../../evil.txt" to byteArrayOf(1))))
        assertTrue("含 ../ 的条目必须被拒绝", data is PackageData.Invalid)
    }

    @Test
    fun `拒绝绝对路径条目`() {
        val src = note()
        val data = read(writeSingleBytes(src, mapOf("/etc/passwd" to byteArrayOf(1))))
        assertTrue(data is PackageData.Invalid)
    }

    @Test
    fun `拒绝含空段的条目`() {
        val src = note()
        val data = read(writeSingleBytes(src, mapOf("a//b.txt" to byteArrayOf(1))))
        assertTrue(data is PackageData.Invalid)
    }

    // ── 安全：损坏与限额 ───────────────────────────────────────

    @Test
    fun `非压缩数据判为无效而不崩溃`() {
        assertTrue(read(byteArrayOf(1, 2, 3, 4, 5)) is PackageData.Invalid)
    }

    @Test
    fun `空输入判为无效`() {
        assertTrue(read(ByteArray(0)) is PackageData.Invalid)
    }

    @Test
    fun `合法 gzip 但非本应用格式判为无效`() {
        val bogus = ByteArrayOutputStream().also { bos ->
            GZIPOutputStream(bos).use { it.write("hello".toByteArray()) }
        }.toByteArray()
        assertTrue(read(bogus) is PackageData.Invalid)
    }

    @Test
    fun `条目声明的尺寸超大时被拒绝`() {
        // 手工构造 tar：一个声明 512MB 的条目（远超 256MB 上限），不提供实际数据。
        // 读取方应在「读数据之前」就发现超限并判为无效（不会尝试分配内存）。
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            val header = ByteArray(512)
            val name = "attachments/big.bin"
            System.arraycopy(name.toByteArray(), 0, header, 0, name.length)
            "0000644\u0000".toByteArray().copyInto(header, 100)
            "0000000\u0000".toByteArray().copyInto(header, 108)
            "0000000\u0000".toByteArray().copyInto(header, 116)
            // 512MB = 0o40000000000
            String.format("%011o", 512L * 1024 * 1024).toByteArray().copyInto(header, 124)
            header[135] = ' '.code.toByte()
            "00000000000\u0000".toByteArray().copyInto(header, 136)
            header[156] = '0'.code.toByte()
            for (i in 148..155) header[i] = ' '.code.toByte()
            var sum = 0
            for (b in header) sum += b.toInt() and 0xFF
            String.format("%06o", sum).toByteArray().copyInto(header, 148)
            header[154] = 0
            header[155] = ' '.code.toByte()
            gz.write(header)
            // 故意不写数据体
        }

        val data = read(bos.toByteArray())
        assertTrue("超大条目必须被拒绝且不 OOM", data is PackageData.Invalid)
    }
}
