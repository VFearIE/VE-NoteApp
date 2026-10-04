package com.noteVE.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.method.ArrowKeyMovementMethod
import android.text.TextWatcher
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import java.util.regex.Pattern

/**
 * 行内链接富文本 EditText。
 *
 * ## 能力
 * ① **自动识别**：输入/加载时把文本中的网址 / 邮箱 / 电话识别为链接；
 * ② **点击打开**：命中链接时弹出系统「打开方式」选择器（ACTION_VIEW + createChooser）。
 * 链接以浅蓝 + 下划线渲染（URLSpan 默认下划线）。
 *
 * ## ★ 网址识别的尺度（为什么不用 Linkify.WEB_URLS）
 *
 * 系统的 `Linkify.WEB_URLS` 为了兼容「裸域名」（如 `example.com`），
 * 其正则**不要求 scheme**，导致误报 —— 典型如把报错堆栈/代码里的
 * `com.foo.Bar.kt:12` 当成网址（`.kt` 也符合它的 TLD 形态）。
 *
 * 这里收窄为**必须是显式 URL**：
 *
 * | 形式 | 是否识别 | 例 |
 * |---|---|---|
 * | 带 scheme | ✅ | `https://a.com/x`、`http://192.168.1.1:8080`、`ftp://a.com` |
 * | `www.` 前缀 | ✅（自动补 `http://`） | `www.a.com` |
 * | 裸域名 | ❌ | `example.com` |
 * | 代码 / 堆栈 / 包名 | ❌ | `com.foo.Bar`、`Foo.kt:12`、`java.lang.NPE` |
 *
 * 并自动**裁掉尾部的成对/句读标点**（中英文），
 * 故「详情见 https://a.com。」这类写法也能得到正确范围。
 *
 * 邮箱与电话仍交给系统内置正则（它们的精确度足够，且不涉及上述误报）。
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

        /**
         * URL 匹配「结尾不允许是标点」的字符集（中英文句读 + 成对符号）。
         *
         * 用法：正则写成 `\S*[^…标点…]` —— `\S*` 贪婪吃到底，
         * 再由末尾字符类**回溯**到最后一个非标点字符，
         * 从而一次性完成「匹配 + 裁尾」，无需额外回调。
         */
        private const val PUNCT = ".,;:!?'\"\\[\\](){}<>" +
            "，。；：！？、（）【】《》「」『』“”‘’·"

        /** 显式带 scheme 的 URL。 */
        private val SCHEME_URL: Pattern = Pattern.compile(
            "(?i)\\b(?:https?://|ftp://)\\S*[^\\s$PUNCT]"
        )

        /** `www.` 前缀形式（要求前面不是单词字符或 `/`，避免与上面的完整 URL 重叠匹配）。 */
        private val WWW_URL: Pattern = Pattern.compile(
            "(?i)(?<![\\w/])www\\.\\S*[^\\s$PUNCT]"
        )

        /** 快速预判：文本里出现这些片段才值得跑正则，避免每次按键都全量扫描。 */
        private fun mayContainLink(t: CharSequence): Boolean {
            val s = t.toString()
            return s.contains("://") || s.contains("www.") || s.contains('@')
        }
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
                    // 清理已失效的链接 span（例如其覆盖的文字被删除）
                    for (span in s.getSpans(0, s.length, URLSpan::class.java)) {
                        val st = s.getSpanStart(span)
                        val en = s.getSpanEnd(span)
                        if (st < 0 || en <= st || en > s.length) s.removeSpan(span)
                    }

                    // 快速预判：文本里没有链接特征就不做任何扫描
                    if (!mayContainLink(s)) return

                    // ① 邮箱 / 电话：沿用系统内置正则
                    //    ★ 必须放在自定义 URL 之前 —— Linkify.addLinks(text, mask)
                    //      会先清空已有的 URLSpan，若顺序反了会把下面刚加的网址冲掉。
                    Linkify.addLinks(s, Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)

                    // ② 网址：仅识别显式 URL（见类注释）
                    applyUrlSpans(s)
                } catch (_: Throwable) {
                    // 识别失败绝不能影响输入
                } finally {
                    linkifying = false
                }
            }
        })
    }

    /** 扫描并写入网址 span。 */
    private fun applyUrlSpans(s: Editable) {
        var m = SCHEME_URL.matcher(s)
        while (m.find()) {
            val start = m.start()
            s.setSpan(
                URLSpan(m.group()),
                start, m.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        m = WWW_URL.matcher(s)
        while (m.find()) {
            // `www.` 形式补上 scheme —— 否则 ACTION_VIEW 无法打开（Uri 无 scheme）
            val start = m.start()
            s.setSpan(
                URLSpan("http://" + m.group()),
                start, m.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
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
     * 三项同时满足才算命中：
     *   ① y 落在该行的 `[lineTop, lineBottom)` 内（排除行间空隙）
     *   ② offset 落在 span 的 `[start, end)` 内（不做 ±1 扩宽）
     *   ③ x 落在该 span 的实际水平范围 `[left - tol, right + tol]` 内
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
