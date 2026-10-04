package com.noteVE.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * 矢量排布：笔记由「块」序列组成，文字 / 图片 / 录音 / 任意文件块自由穿插。
 * 图片/录音/文件块占满左右宽度、占整数行高度，禁止同行键入。
 *
 * [File] 用于「任意文件」：按扩展名区分视频/音频/其它，
 * 显示为小矩形卡片（文件名 + 尺寸），视频额外显示缩略图。
 */
sealed class Block {
    data class Text(val html: String) : Block()
    data class Image(val path: String) : Block()
    data class Audio(val path: String) : Block()
    /** 任意文件（视频/音频/文档等）。path 为附件目录下的文件名。 */
    data class File(val path: String) : Block()
}

object BlockSerializer {

    fun toJson(blocks: List<Block>): String {
        val arr = JSONArray()
        for (b in blocks) {
            val o = JSONObject()
            when (b) {
                is Block.Text -> { o.put("type", "text"); o.put("html", b.html) }
                is Block.Image -> { o.put("type", "image"); o.put("path", b.path) }
                is Block.Audio -> { o.put("type", "audio"); o.put("path", b.path) }
                is Block.File -> { o.put("type", "file"); o.put("path", b.path) }
            }
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<Block> {
        val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        val out = mutableListOf<Block>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (o.optString("type")) {
                "image" -> out.add(Block.Image(o.optString("path")))
                "audio" -> out.add(Block.Audio(o.optString("path")))
                "file" -> out.add(Block.File(o.optString("path")))
                else -> out.add(Block.Text(o.optString("html")))
            }
        }
        return out
    }

    /** 提取纯文本（列表预览 + 全文检索）。 */
    fun toPlainText(blocks: List<Block>): String {
        val sb = StringBuilder()
        for (b in blocks) {
            if (b is Block.Text) {
                sb.append(HtmlEx.fromHtml(b.html).toString()).append('\n')
            }
        }
        return sb.toString().trim()
    }

    /** 提取所有图片与录音路径（用于附件清理）。 */
    fun attachmentPaths(blocks: List<Block>): Triple<List<String>, List<String>, List<String>> {
        val images = mutableListOf<String>()
        val audios = mutableListOf<String>()
        val files = mutableListOf<String>()
        for (b in blocks) {
            when (b) {
                is Block.Image -> images.add(b.path)
                is Block.Audio -> audios.add(b.path)
                is Block.File -> files.add(b.path)
                else -> {}
            }
        }
        return Triple(images, audios, files)
    }
}
