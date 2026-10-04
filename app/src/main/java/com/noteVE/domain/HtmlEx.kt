package com.noteVE.domain

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import org.xml.sax.XMLReader

/** 有序列表段落样式：首行绘制行号。 */
class OrderedListSpan(val number: Int) : LeadingMarginSpan.Standard(48), ParagraphStyle {
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        if (first) {
            val saved = p.style
            p.style = Paint.Style.FILL
            c.drawText("$number.", x.toFloat(), baseline.toFloat(), p)
            p.style = saved
        }
    }
}

/**
 * Spanned <-> HTML 双向转换。
 * 内部存储格式：加粗 b / 斜体 i / 下划线 u / 删除线 s / 链接 a / 颜色 font / 无序 ul+li / 有序 olist+oitem。
 * 有序列表使用自定义标签 olist/oitem，避免与系统 Html 的原生 <ol> 处理冲突。
 */
object HtmlEx {

    /**
     * HTML → Spanned。
     *
     * @param darkMode 深色模式下，把**近黑色**文字适配为白色（否则黑字黑底不可读）。
     *   只处理近黑色，其余颜色（红/橙/绿/蓝/紫…）原样保留 —— 那些在深浅底下都清晰。
     */
    fun fromHtml(html: String, darkMode: Boolean = false): Spanned {
        val spanned = HtmlCompat.fromHtml(
            html, HtmlCompat.FROM_HTML_MODE_LEGACY, null, OrderedListTagHandler()
        )
        return if (darkMode) adaptForDarkMode(spanned) else spanned
    }

    /**
     * 深色适配：近黑 → 近白。
     * 判据：RGB 三通道都 < [DARK_THRESHOLD] 视为"近黑"（含纯黑 #000000 与深灰）。
     * 实现：重建一个 Spannable，把命中的 ForegroundColorSpan 换成白色，其余 span 原样搬运。
     */
    private const val DARK_THRESHOLD = 0x40   // 64/255

    private fun adaptForDarkMode(src: Spanned): Spanned {
        val text = src.toString()
        val out = android.text.SpannableString(text)
        for (span in src.getSpans(0, src.length, ForegroundColorSpan::class.java)) {
            val c = span.foregroundColor
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val nearBlack = r < DARK_THRESHOLD && g < DARK_THRESHOLD && b < DARK_THRESHOLD
            val replacement = if (nearBlack) ForegroundColorSpan(0xFFFFFFFF.toInt()) else span
            val s = src.getSpanStart(span)
            val e = src.getSpanEnd(span)
            if (s >= 0 && e > s) {
                out.setSpan(replacement, s, e, src.getSpanFlags(span))
            }
        }
        // 搬运其余 span（粗体/斜体/链接/列表等）
        for (span in src.getSpans(0, src.length, Any::class.java)) {
            if (span is ForegroundColorSpan) continue
            val s = src.getSpanStart(span)
            val e = src.getSpanEnd(span)
            if (s >= 0 && e > s) out.setSpan(span, s, e, src.getSpanFlags(span))
        }
        return out
    }

    fun toHtml(spanned: Spanned): String {
        val text = spanned.toString()
        val out = StringBuilder()
        var listType = 0 // 0 none, 1 ordered, 2 bullet
        var i = 0
        val n = text.length
        while (i < n) {
            val nl = text.indexOf('\n', i)
            val end = if (nl < 0) n else nl
            val pType = paragraphListType(spanned, i, end)

            if (pType != listType) {
                when (listType) {
                    1 -> out.append("</olist>")
                    2 -> out.append("</ul>")
                }
                when (pType) {
                    1 -> out.append("<olist>")
                    2 -> out.append("<ul>")
                }
                listType = pType
            }

            when (pType) {
                1 -> { out.append("<oitem>"); inline(spanned, i, end, out); out.append("</oitem>") }
                2 -> { out.append("<li>"); inline(spanned, i, end, out); out.append("</li>") }
                else -> inline(spanned, i, end, out)
            }

            i = if (nl < 0) n else nl + 1
            if (pType == 0 && i < n) out.append("<br>")
        }
        when (listType) {
            1 -> out.append("</olist>")
            2 -> out.append("</ul>")
        }
        return out.toString()
    }

    /** 单条导出用：把内部有序标签转成标准 HTML。 */
    fun toExportHtml(spanned: Spanned): String =
        toHtml(spanned)
            .replace("<olist>", "<ol>").replace("</olist>", "</ol>")
            .replace("<oitem>", "<li>").replace("</oitem>", "</li>")

    private fun paragraphListType(spanned: Spanned, start: Int, end: Int): Int {
        if (start >= end) return 0
        val spans = spanned.getSpans(start, start + 1, ParagraphStyle::class.java)
        if (spans.any { it is OrderedListSpan }) return 1
        if (spans.any { it is BulletSpan }) return 2
        return 0
    }

    private fun inline(spanned: Spanned, start: Int, end: Int, out: StringBuilder) {
        val text = spanned.toString()
        var i = start
        while (i < end) {
            var next = end
            val spans = spanned.getSpans(i, end, CharacterStyle::class.java)
            for (span in spans) {
                val s = spanned.getSpanStart(span)
                val e = spanned.getSpanEnd(span)
                if (s > i && s < next) next = s
                if (e > i && e < next) next = e
            }
            for (span in spans) if (spanned.getSpanEnd(span) == i) out.append(closeTag(span))
            out.append(escape(text.substring(i, next)))
            if (next < end) {
                val nxt = spanned.getSpans(next, next + 1, CharacterStyle::class.java)
                for (span in nxt) if (spanned.getSpanStart(span) == next) out.append(openTag(span))
            }
            i = next
        }
    }

    private fun openTag(span: CharacterStyle): String = when (span) {
        is StyleSpan -> {
            var s = ""
            if (span.style and Typeface.BOLD != 0) s += "<b>"
            if (span.style and Typeface.ITALIC != 0) s += "<i>"
            s
        }
        is UnderlineSpan -> "<u>"
        is StrikethroughSpan -> "<s>"
        is URLSpan -> "<a href=\"${span.url}\">"
        is ForegroundColorSpan -> "<font color=\"#${String.format("%06X", 0xFFFFFF and span.foregroundColor)}\">"
        else -> ""
    }

    private fun closeTag(span: CharacterStyle): String = when (span) {
        is StyleSpan -> {
            var s = ""
            if (span.style and Typeface.ITALIC != 0) s += "</i>"
            if (span.style and Typeface.BOLD != 0) s += "</b>"
            s
        }
        is UnderlineSpan -> "</u>"
        is StrikethroughSpan -> "</s>"
        is URLSpan -> "</a>"
        is ForegroundColorSpan -> "</font>"
        else -> ""
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private class OrderedListTagHandler : android.text.Html.TagHandler {
        private var index = 0
        private val starts = mutableListOf<Pair<Int, Int>>()

        override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
            when (tag.lowercase()) {
                "olist" -> index = 0
                "oitem" -> {
                    if (opening) {
                        if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                        index++
                        starts.add(output.length to index)
                    } else {
                        val (start, num) = starts.removeLastOrNull() ?: (output.length to ++index)
                        if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                        if (output.length > start) {
                            output.setSpan(OrderedListSpan(num), start, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }
        }
    }
}
