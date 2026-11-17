package com.noteVE.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AttachmentNamesTest {
    private fun sandbox(run: (File) -> Unit) {
        val dir = Files.createTempDirectory("ve-attachment-test").toFile()
        try { run(dir) } finally { dir.deleteRecursively() }
    }
    @Test fun rejectsUnsafeNamesAndNamespaces() {
        for (name in listOf("", " ", ".", "..", "../db", "/db", "a\\b", "a:b", "x\u0000", "中".repeat(86))) {
            assertThrows(IllegalArgumentException::class.java) { AttachmentNames.requireSafe(name) }
        }
        for (key in listOf("a", "other/a", "attachments/../a", "recordings/", "attachments/a/b")) {
            assertThrows(IllegalArgumentException::class.java) { AttachmentNames.requireKey(key) }
        }
        assertEquals("recordings/ok.m4a", AttachmentNames.requireKey("recordings/ok.m4a"))
    }
    @Test fun symlinksCannotEscapeOrCrossTypes() = sandbox { root ->
        val attachments = File(root, "attachments").apply { mkdir() }
        val recordings = File(root, "recordings").apply { mkdir() }
        val voice = File(recordings, "a").apply { writeText("private voice") }
        Files.createSymbolicLink(File(attachments, "alias").toPath(), voice.toPath())
        assertThrows(IllegalArgumentException::class.java) { AttachmentNames.resolve(attachments, "alias") }
        val aliasDir = File(root, "alias-dir")
        Files.createSymbolicLink(aliasDir.toPath(), recordings.toPath())
        // Android may expose app data through a compatibility symlink; the trusted
        // directory itself may resolve, while a file symlink must still be rejected.
        assertEquals(voice.canonicalFile, AttachmentNames.resolve(aliasDir, "a"))
        assertEquals(voice.canonicalFile, AttachmentNames.resolve(recordings, "a"))
    }
    @Test fun importNeverOverwritesAndRewritesEveryReference() = sandbox { root ->
        val old = File(root, "same.bin").apply { writeText("original") }
        val blocks = listOf(Block.Text("hello"), Block.Image("same.bin"), Block.File("same.bin"), Block.Audio("same.bin"))
        fun import(): List<Block> = AttachmentNames.rewriteBlocks(blocks) { key ->
            val dir = File(root, key.substringBefore('/'))
            AttachmentNames.reserve(dir, "same.bin").apply { writeText(key) }.name
        }
        val first = import(); val second = import()
        assertEquals("original", old.readText())
        assertEquals(first[1].let { (it as Block.Image).path }, (first[2] as Block.File).path)
        assertNotEquals((first[2] as Block.File).path, (first[3] as Block.Audio).path)
        assertNotEquals(first[1], second[1])
        assertEquals(blocks[0], first[0])
        assertEquals("attachments/same.bin", File(root, "attachments/${(first[1] as Block.Image).path}").readText())
        assertEquals("recordings/same.bin", File(root, "recordings/${(first[3] as Block.Audio).path}").readText())
    }
    @Test fun displayTruncationDoesNotSplitEmoji() {
        val raw = "a" + "😀".repeat(80)
        val got = AttachmentNames.displayBase(raw)
        assertTrue(got.toByteArray(Charsets.UTF_8).size <= 180)
        assertFalse(Character.isHighSurrogate(got.last()))
        assertEquals(got, String(got.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }
}
