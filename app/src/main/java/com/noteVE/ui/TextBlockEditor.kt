package com.noteVE.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.noteVE.domain.HtmlEx

/**
 * 单块文字编辑器（矢量排布中的一个文字块）。
 * lastEmittedMap 由父级持有，用于格式化时同步避免光标跳位。
 * minHeight 用于让末个文字块撑满剩余视口（点击空白即可定位光标）。
 * focusRequest == blockIndex 时请求焦点并把光标移到开头。
 */
@Composable
fun TextBlockEditor(
    initialHtml: String,
    lastEmittedMap: SnapshotStateMap<Int, String>,
    blockIndex: Int,
    onHtmlChange: (String) -> Unit,
    onFocused: (EditText) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
    focusRequest: Int? = null,
    onFocusConsumed: () -> Unit = {},
    /** 深色模式下把近黑文字渲染为白色（其他颜色不变）。 */
    darkMode: Boolean = false,
    /**
     * 只读模式：把 EditText 设为 disabled。
     * disabled 的 View **不接收触摸**（触摸会交给父容器处理滚动）、**不获取焦点**
     * → 点哪里都不会弹输入法，也无法输入/删除。文字颜色用 setTextColor(Int) 显式设置，
     * 不受 disabled 的 ColorStateList 影响，故不会变灰。
     */
    readOnly: Boolean = false
) {
    val currentOnHtmlChange by rememberUpdatedState(onHtmlChange)
    val currentOnFocused by rememberUpdatedState(onFocused)
    val currentOnFocusConsumed by rememberUpdatedState(onFocusConsumed)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            NoteEditText(ctx).apply {
                setText(HtmlEx.fromHtml(initialHtml, darkMode))
                lastEmittedMap[blockIndex] = initialHtml
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val d = resources.displayMetrics.density
                val pad = (14 * d).toInt()
                if (minHeight.value > 0f) minimumHeight = (minHeight.value * d).toInt()
                setOnFocusChangeListener { _, hasFocus -> if (hasFocus) currentOnFocused(this) }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (s == null) return
                        val html = HtmlEx.toHtml(s)
                        if (html != lastEmittedMap[blockIndex]) {
                            lastEmittedMap[blockIndex] = html
                            currentOnHtmlChange(html)
                        }
                    }
                })
            }
        },
        update = { et ->
            et.applyLinkColor(darkMode)
            // 只读：禁用（不接收触摸/不获焦点/不弹输入法）
            if (et.isEnabled == readOnly) et.isEnabled = !readOnly
            val d = et.resources.displayMetrics.density
            et.minimumHeight = if (minHeight.value > 0f) (minHeight.value * d).toInt() else 0
            if (initialHtml != lastEmittedMap[blockIndex]) {
                lastEmittedMap[blockIndex] = initialHtml
                et.setText(HtmlEx.fromHtml(initialHtml, darkMode))
            }
            // 插入后把光标定位到该块（图片/录音后面的文字块）——只读时不抢焦点
            if (!readOnly && focusRequest == blockIndex) {
                et.requestFocus()
                et.setSelection(0)
                currentOnFocusConsumed()
            }
        }
    )
}
