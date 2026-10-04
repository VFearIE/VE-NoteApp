package com.noteVE.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.method.ArrowKeyMovementMethod
import android.text.TextWatcher
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText

/**
 * 行内链接富文本 EditText。
 *
 * 修复两点：
 * ① 自动识别：输入/加载时用 Linkify 实时把裸 URL / 邮箱 / 电话识别为链接；
 * ② 点击可打开：命中链接时弹出系统「打开方式」选择器（ACTION_VIEW + createChooser）。
 * 链接以浅蓝色 + 下划线渲染（URLSpan/ClickableSpan 默认下划线）。
 */
class NoteEditText(context: Context) : EditText(context) {

    companion object {
        /** 链接命中容差（像素）：仅容忍边缘 1~2 px，不影响「点空白不跳转」。 */
        private const val HIT_TOLERANCE_PX = 2f

        /** 链接色：深色底浅蓝 / 浅色底深蓝 */
        private const val LINK_DARK = 0xFF4FC3F7.toInt()
        private const val LINK_LIGHT = 0xFF1565C0.toInt()
        /** 正文色（与 Compose 主题 onSurface 一致） */
        private const val TEXT_DARK = 0xFFE6E4EC.toInt()
        private const val TEXT_LIGHT = 0xFF1A1B20.toInt()
        /** 占位符（onSurfaceVariant） */
        private const val HINT_DARK = 0xFFC6C5D0.toInt()
        private const val HINT_LIGHT = 0xFF45464F.toInt()
    }

    private var linkifying = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        textSize = 18f
        setLineSpacing(0f, 1.25f)
        val d = resources.displayMetrics.density
        val pad = (14 * d).toInt()
        setPadding(pad, pad, pad, pad)
        // ★ 选中高亮必须可见（曾误设为全透明 0x00000000 → 长按选中看不到任何反馈，
        //   用户以为无法选中/无选择手柄）。此处用半透明主色，浅深底都清晰。
        highlightColor = 0x554F53AE.toInt()
        // 保证长按可选择、可拖动选择手柄
        setTextIsSelectable(true)
        isFocusableInTouchMode = true
        isCursorVisible = true
        // 默认按深色着色；实际值由 Compose 侧调用 applyTheme(darkMode) 覆盖
        applyTheme(true)

        // ★★ 必须用 ArrowKeyMovementMethod（EditText 默认值），**不能用 LinkMovementMethod**。
        //   原因：LinkMovementMethod.onKeyDown 会拦截【方向键】用于在链接间跳转并 return true，
        //   导致输入法的「光标移动 / 选择 / 开头 / 末尾」全部失效
        //   （等价于蓝牙键盘的上下左右、Home、End、Shift+方向键都失灵）。
        //   链接点击**不依赖** LinkMovementMethod —— 已在 onTouchEvent 里自行命中测试并打开。
        movementMethod = ArrowKeyMovementMethod.getInstance()

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null || linkifying) return
                linkifying = true
                try {
                    // 清理失效链接，再整体重新识别
                    for (span in s.getSpans(0, s.length, URLSpan::class.java)) {
                        val st = s.getSpanStart(span)
                        val en = s.getSpanEnd(span)
                        if (st < 0 || en <= st || en > s.length) s.removeSpan(span)
                    }
                    Linkify.addLinks(
                        s,
                        Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
                    )
                } catch (_: Throwable) {
                } finally {
                    linkifying = false
                }
            }
        })
    }

    /**
     * 按当前主题应用文字与链接配色。
     *
     * ★ 为什么必须显式设置（不能靠 XML 主题）：
     *   本控件由 Compose 的 AndroidView 承载，读的是 **Activity 的 XML 主题**；
     *   而应用内的深色开关是 Compose 层状态（MaterialTheme），二者不同步。
     *   若只在 XML 里做 `-night` 变体，用户「手动选深色但系统仍是浅色」时就会失效。
     *   故由 Compose 侧把 [darkMode] 传进来，直接设置控件颜色 —— 最可靠。
     */
    fun applyTheme(darkMode: Boolean) {
        val text = if (darkMode) TEXT_DARK else TEXT_LIGHT
        setTextColor(text)
        setHintTextColor(if (darkMode) HINT_DARK else HINT_LIGHT)
        setLinkTextColor(if (darkMode) LINK_DARK else LINK_LIGHT)
    }

    /** 兼容旧调用名。 */
    fun applyLinkColor(darkMode: Boolean) = applyTheme(darkMode)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 命中链接：直接交给系统选择「打开方式」，不移动光标
        if (event.action == MotionEvent.ACTION_UP) {
            val url = urlAt(event.x, event.y)
            if (url != null) {
                openUrl(url)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 精确命中测试：只有**点击位置真正落在链接文字上**才返回 URL。
     *
     * ★ 修复「点击范围过大」：旧实现只按水平偏移取 offset，再用 `off±1` 取 span，
     *   导致①整行高度内任意位置（含行间空白）都命中 ②行末之后的空白也算到行末 offset。
     *   现要求三项同时满足：
     *     ① y 落在该行的 [lineTop, lineBottom) 内（排除行间空隙）
     *     ② offset 落在 span 的 [start, end) 内（去掉 ±1 扩宽）
     *     ③ x 落在该 span 的实际水平范围 [spanLeft - tol, spanRight + tol] 内
     *        （tiny tolerance 仅用于容忍边缘像素，不影响「点空白不跳转」）
     */
    private fun urlAt(x: Float, y: Float): String? = try {
        val lay = layout
        val t = text
        if (lay == null || t == null || t.isEmpty()) {
            null
        } else {
            val vx = x - totalPaddingLeft
            val vy = y - totalPaddingTop

            // ① 垂直方向必须落在某一行内（越界直接不命中）
            if (vy < 0f || vy > lay.height.toFloat()) {
                null
            } else {
                val line = lay.getLineForVertical(vy.toInt())
                val lineTop = lay.getLineTop(line)
                val lineBottom = lay.getLineBottom(line)
                if (vy < lineTop || vy >= lineBottom) {
                    null   // 落在行间/行外空白
                } else if (vx < 0f || vx > lay.getLineRight(line)) {
                    null   // 落在该行文字右端之外的空白
                } else {
                    val off = lay.getOffsetForHorizontal(line, vx)
                    val spans = t.getSpans(off, off, URLSpan::class.java)
                    // ② offset 必须真在 span 内（getSpans(off,off) 本身已要求 off∈[start,end]）
                    val span = spans.firstOrNull { sp ->
                        val ss = t.getSpanStart(sp)
                        val se = t.getSpanEnd(sp)
                        off in ss until se
                    }
                    if (span == null) {
                        null
                    } else {
                        // ③ 水平方向必须落在该 span 的绘制范围内
                        val ss = t.getSpanStart(span)
                        val se = t.getSpanEnd(span)
                        val left = lay.getPrimaryHorizontal(ss)
                        val right = lay.getPrimaryHorizontal(se)
                        val lo = (minOf(left, right) - HIT_TOLERANCE_PX)
                        val hi = (maxOf(left, right) + HIT_TOLERANCE_PX)
                        if (vx < lo || vx > hi) null else span.url
                    }
                }
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun openUrl(url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val chooser = Intent.createChooser(view, context.getString(com.noteVE.R.string.open_link))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
