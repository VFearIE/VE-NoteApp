package com.noteVE.domain

import java.io.File
import java.util.UUID

/** All persisted attachment paths are basenames, never caller-controlled relative paths. */
object AttachmentNames {
    fun requireSafe(name: String): String {
        require(name.isNotBlank() && name != "." && name != "..") { "Empty attachment name" }
        require(name.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }) { "Unsafe attachment name" }
        require(name.toByteArray(Charsets.UTF_8).size <= 255) { "Attachment name too long" }
        return name
    }

    fun resolve(dir: File, name: String): File {
        requireSafe(name)
        // The app data root may itself be exposed through Android's /data/user/0
        // compatibility symlink. Resolve the trusted directory once, then enforce
        // containment against that canonical directory; rejecting the directory
        // symlink itself makes every existing installation unreadable.
        val base = dir.canonicalFile
        require(base.isDirectory) { "Attachment directory is unavailable" }
        val target = File(base, name)
        require(target.canonicalFile.parentFile == base) {
            "Attachment escapes its type directory"
        }
        return target
    }

    fun key(block: Block): String? = when (block) {
        is Block.Image -> "attachments/${requireSafe(block.path)}"
        is Block.File -> "attachments/${requireSafe(block.path)}"
        is Block.Audio -> "recordings/${requireSafe(block.path)}"
        is Block.Text -> null
    }

    fun requireKey(key: String): String {
        val dir = key.substringBefore('/')
        require(dir == "attachments" || dir == "recordings") { "Unknown attachment namespace" }
        requireSafe(key.substringAfter('/', ""))
        return key
    }

    fun bytesFor(block: Block, bytes: Map<String, ByteArray>): ByteArray {
        val key = key(block) ?: error("Not an attachment")
        return bytes[key] ?: bytes[key.substringAfter('/')] ?: error("Missing attachment: $key")
    }

    /** One allocation per typed source key, retaining block order and shared references. */
    fun rewriteBlocks(blocks: List<Block>, allocate: (String) -> String): List<Block> {
        val allocated = mutableMapOf<String, String>()
        return blocks.map { block ->
            val key = key(block) ?: return@map block
            val name = allocated.getOrPut(key) { requireSafe(allocate(key)) }
            when (block) {
                is Block.Image -> Block.Image(name)
                is Block.Audio -> Block.Audio(name)
                is Block.File -> Block.File(name)
                is Block.Text -> block
            }
        }
    }

    /** Keep display-friendly names, but bound UTF-8 bytes and strip filesystem metacharacters. */
    fun displayBase(raw: String, fallback: String = "file"): String {
        val clean = raw.trim().map { if (it.isISOControl() || it in "/\\:*?\"<>|") '_' else it }.joinToString("")
        var result = clean.ifBlank { fallback }
        while (result.toByteArray(Charsets.UTF_8).size > 180) {
            result = result.substring(0, result.offsetByCodePoints(result.length, -1))
        }
        return result.takeUnless { it == "." || it == ".." } ?: fallback
    }

    /** Atomically reserve an independent file. Never truncate an existing file. */
    fun reserve(dir: File, original: String): File {
        requireSafe(original)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create attachment directory" }
        val ext = original.substringAfterLast('.', "").takeIf { it.length in 1..12 && it.all(Char::isLetterOrDigit) }
        while (true) {
            val name = "import_${UUID.randomUUID()}" + (ext?.let { ".$it" } ?: "")
            val target = resolve(dir, name)
            if (target.createNewFile()) return target
        }
    }
}
