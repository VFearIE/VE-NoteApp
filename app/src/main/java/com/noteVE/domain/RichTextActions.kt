package com.noteVE.domain

import android.graphics.Typeface
import android.text.Editable
import android.text.Selection
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan

/** 作用于编辑区选中文本的富文本动作（按行分段应用，避免跨换行的样式跨度）。 */
object RichTextActions {

    fun toggleBold(e: Editable) = toggleStyle(e, Typeface.BOLD)
    fun toggleItalic(e: Editable) = toggleStyle(e, Typeface.ITALIC)
    fun toggleUnderline(e: Editable) = toggleCharSpan(e) { UnderlineSpan() }
    fun toggleStrike(e: Editable) = toggleCharSpan(e) { StrikethroughSpan() }
    fun toggleBullet(e: Editable) = toggleList(e, ordered = false)
    fun toggleOrdered(e: Editable) = toggleList(e, ordered = true)

    /**
     * 应用链接。
     *
     * @param range 显式选区 `(start, end)`；**传 null 才回退读取当前 selection**。
     *
     * ★ 为什么需要显式选区：点击工具栏按钮会弹出对话框，对话框会夺走 EditText 焦点，
     *   此时 `Selection.getSelectionStart()` 已不再等于用户点击工具栏前选中的范围
     *   （表现为「插了链接但没作用在选中文字上」）。
     *   故调用方必须在**点击瞬间**冻结选区，并把冻结值传进来。
     */
    fun applyLink(e: Editable, url: String, range: Pair<Int, Int>? = null) {
        val (s, end) = resolveRange(e, range) ?: return
        for ((ls, le) in lineSegments(e, s, end)) {
            e.getSpans(ls, maxOf(ls, le - 1), URLSpan::class.java).forEach { e.removeSpan(it) }
            e.setSpan(URLSpan(url), ls, le, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 应用文字颜色。同样支持显式选区（颜色对话框也会抢焦点）。 */
    fun applyColor(e: Editable, color: Int, range: Pair<Int, Int>? = null) {
        val (s, end) = resolveRange(e, range) ?: return
        for ((ls, le) in lineSegments(e, s, end)) {
            e.getSpans(ls, maxOf(ls, le - 1), ForegroundColorSpan::class.java).forEach { e.removeSpan(it) }
            e.setSpan(ForegroundColorSpan(color), ls, le, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * 解析有效选区：优先用调用方冻结的 [range]，否则读当前 selection；
     * 统一 clamp 到 [0, length] 并要求 start < end，否则返回 null（空选区不产生动作）。
     */
    private fun resolveRange(e: Editable, range: Pair<Int, Int>?): Pair<Int, Int>? {
        val raw = range ?: selection(e) ?: return null
        val len = e.length
        val a = raw.first.coerceIn(0, len)
        val b = raw.second.coerceIn(0, len)
        val s = minOf(a, b)
        val t = maxOf(a, b)
        return if (t > s) (s to t) else null
    }

    private fun selection(e: Editable): Pair<Int, Int>? {
        val s = Selection.getSelectionStart(e)
        val en = Selection.getSelectionEnd(e)
        if (s < 0 || en < 0) return null
        return if (s <= en) s to en else en to s
    }

    private fun toggleStyle(e: Editable, flag: Int) {
        val (s, en) = selection(e) ?: return
        if (s == en) return
        val removing = hasStyleAt(e, s, flag)
        for ((ls, le) in lineSegments(e, s, en)) {
            val spans = e.getSpans(ls, maxOf(ls, le - 1), StyleSpan::class.java)
            for (span in spans) {
                val ss = e.getSpanStart(span); val se = e.getSpanEnd(span)
                e.removeSpan(span)
                val ns = if (removing) span.style and flag.inv() else span.style or flag
                if (ns != 0) e.setSpan(StyleSpan(ns), ss, se, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (!removing) e.setSpan(StyleSpan(flag), ls, le, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggleCharSpan(e: Editable, factory: () -> CharacterStyle) {
        val (s, en) = selection(e) ?: return
        if (s == en) return
        val cls = factory().javaClass
        val removing = hasSpanAt(e, s, cls)
        for ((ls, le) in lineSegments(e, s, en)) {
            e.getSpans(ls, maxOf(ls, le - 1), cls).forEach { e.removeSpan(it) }
            if (!removing) e.setSpan(factory(), ls, le, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggleList(e: Editable, ordered: Boolean) {
        val (s, en) = selection(e) ?: return
        val firstStart = e.lastIndexOf('\n', s - 1) + 1
        val nlAfter = e.indexOf('\n', en)
        val lastEnd = if (nlAfter < 0) e.length else nlAfter

        val target = if (ordered) OrderedListSpan::class.java else BulletSpan::class.java
        val opposite = if (ordered) BulletSpan::class.java else OrderedListSpan::class.java

        val starts = mutableListOf<Int>()
        var p = firstStart
        while (true) {
            starts.add(p)
            val nl = e.indexOf('\n', p)
            if (nl < 0 || nl >= lastEnd) break
            p = nl + 1
        }

        val removing = starts.any { hasSpanAt(e, it, target) }

        for (ps in starts) {
            e.getSpans(ps, minOf(ps + 1, e.length), target).forEach { e.removeSpan(it) }
            e.getSpans(ps, minOf(ps + 1, e.length), opposite).forEach { e.removeSpan(it) }
        }
        if (!removing) {
            var num = 1
            for (ps in starts) {
                val nl = e.indexOf('\n', ps)
                val pe = if (nl < 0) e.length else nl
                if (pe > ps) {
                    val span = if (ordered) OrderedListSpan(num++) else BulletSpan(24)
                    e.setSpan(span, ps, pe, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun hasStyleAt(e: Editable, pos: Int, flag: Int): Boolean =
        e.getSpans(pos, minOf(pos + 1, e.length), StyleSpan::class.java).any { it.style and flag != 0 }

    private fun hasSpanAt(e: Editable, pos: Int, cls: Class<*>): Boolean =
        e.getSpans(pos, minOf(pos + 1, e.length), cls).isNotEmpty()

    private fun lineSegments(e: Editable, s: Int, en: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var p = s
        while (p < en) {
            val nl = e.indexOf('\n', p)
            val le = if (nl < 0 || nl >= en) en else nl
            out.add(p to le)
            if (nl < 0 || nl >= en) break
            p = nl + 1
        }
        return out
    }
}
