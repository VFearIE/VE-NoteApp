package com.noteVE.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Hand-built v1 fixtures bypass the trusted writer, exercising hostile input directly. */
class NotePackageSecurityTest {
    private fun manifest(path: String? = null): String = JSONObject().apply {
        put("title", "old"); put("createdAt", 1); put("modifiedAt", 2); put("lastOpenedAt", 3)
        put("pinned", true); put("labels", JSONArray(listOf("legacy")))
        put("reminderAt", JSONObject.NULL); put("repeatRule", JSONObject.NULL)
        put("blocks", if (path == null) "[]" else JSONArray().put(JSONObject().put("type", "file").put("path", path)).toString())
    }.toString()

    private fun entry(name: String, bytes: ByteArray, type: Char = '0', size: Long = bytes.size.toLong()): ByteArray {
        val out = ByteArrayOutputStream()
        val h = ByteArray(512)
        name.toByteArray().copyInto(h)
        "%011o".format(size).toByteArray().copyInto(h, 124)
        h[156] = type.code.toByte()
        for (i in 148..155) h[i] = 32
        "%06o".format(h.sumOf { it.toInt() and 255 }).toByteArray().copyInto(h, 148)
        h[154] = 0; h[155] = 32
        out.write(h); out.write(bytes); out.write(ByteArray((512 - bytes.size % 512) % 512))
        return out.toByteArray()
    }
    private fun tar(path: String? = null, extra: ByteArray = ByteArray(0), version: Int = 1, note: String = manifest(path), full: Boolean = false): ByteArray =
        entry("meta.json", """{"format":"noteapp","type":"${if (full) "full" else "single"}","version":$version}""".toByteArray()) +
            entry(if (full) "notes.json" else "note.json", note.toByteArray()) + extra + ByteArray(1024)
    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
    private fun read(bytes: ByteArray): PackageData = NotePackage.read(gzip(bytes).inputStream())
    private fun invalid(bytes: ByteArray) = assertTrue(read(bytes) is PackageData.Invalid)

    @Test fun v1Compatibility() {
        val got = read(tar("a.bin", entry("attachments/a.bin", byteArrayOf(8)))) as PackageData.Single
        assertEquals(listOf("legacy"), got.note.labels)
        assertEquals(listOf(Block.File("a.bin")), got.note.blocks)
        assertArrayEquals(byteArrayOf(8), got.attachments["a.bin"])
    }
    @Test fun jsonOnlyTraversalAndMissingAttachment() {
        for (name in listOf("../../databases/notes.db", "/etc/passwd", "..", "", "a\\b", "a\nb", "recordings/a", "x:y", "good.bin")) invalid(tar(name))
    }
    @Test fun unknownVersionMalformedBlocksAndFullMember() {
        invalid(tar(version = 3))
        invalid(tar(note = JSONObject(manifest()).put("blocks", "broken").toString()))
        invalid(tar(note = JSONObject(manifest()).put("blocks", "[{\"type\":\"unknown\"}]").toString()))
        invalid(tar(note = "[${manifest()},42]", full = true))
    }
    @Test fun truncatedHeadersBodiesPaddingAndTerminators() {
        val src = tar("a.bin", entry("attachments/a.bin", byteArrayOf(4, 5, 6)))
        // Every possible cut in a valid, re-gzipped tar must fail, not recover a prefix.
        for (cut in src.indices) assertTrue("accepted cut $cut", read(src.copyOf(cut)) is PackageData.Invalid)
        assertTrue(read(src) is PackageData.Single)
    }
    @Test fun checksumDuplicatesTypeAndTrailingData() {
        val src = tar()
        invalid(src.copyOf().also { it[0] = (it[0] + 1).toByte() })
        invalid(tar(extra = entry("note.json", manifest().toByteArray())))
        invalid(tar(extra = entry("attachments/link", ByteArray(0), '2')))
        invalid(src + byteArrayOf(1))
        invalid(tar(extra = entry("attachments/big", ByteArray(0), size = 512L * 1024 * 1024)))
        invalid(tar(extra = (1..20001).fold(ByteArrayOutputStream()) { out, i -> out.apply { write(entry("attachments/$i", ByteArray(0))) } }.toByteArray()))
    }
    @Test fun gzipCrcAndTrailerRequired() {
        val good = gzip(tar())
        assertTrue(NotePackage.read(good.copyOf(good.size - 4).inputStream()) is PackageData.Invalid)
        good[good.size - 8] = (good[good.size - 8].toInt() xor 1).toByte()
        assertTrue(NotePackage.read(good.inputStream()) is PackageData.Invalid)
    }
    @Test fun longUtf8AndTypedCollisionsRoundTrip() {
        for (name in listOf("中".repeat(29), "中".repeat(30), "中".repeat(60), "中".repeat(80), "😀".repeat(50))) {
            val blocks = listOf(Block.File("$name.m4a"), Block.Audio("$name.m4a"))
            val n = PackedNote("t", blocks, listOf("old"), 1, 2, 3, true, null, null)
            val bytes = mapOf("attachments/$name.m4a" to byteArrayOf(1), "recordings/$name.m4a" to byteArrayOf(2))
            val out = ByteArrayOutputStream(); NotePackage.writeSingle(out, n, bytes)
            val got = NotePackage.read(out.toByteArray().inputStream()) as PackageData.Single
            assertEquals(n, got.note)
            for ((key, value) in bytes) assertArrayEquals(value, got.attachments[key])
            assertThrows(IllegalArgumentException::class.java) { NotePackage.writeSingle(ByteArrayOutputStream(), n, mapOf("$name.m4a" to byteArrayOf(9))) }
        }
    }
}
