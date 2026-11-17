package com.noteVE.reminder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.noteVE.R

/** Original capsule/handle artwork. A tap on the far end is NOT a dismiss gesture. */
internal class DismissSlider(context: Context, private val dismiss: () -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var fraction = 0f
    private var dragging = false
    private var downX = 0f
    private var pointer = -1
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val handle get() = minOf(64 * density, width * 0.38f)
    private val inset get() = 6 * density
    private val travel get() = (width - handle - inset * 2).coerceAtLeast(1f)
    init {
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(R.string.rv3_slide)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun reset() { fraction = 0f; dragging = false; pointer = -1; invalidate() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((240 * density).toInt(), widthMeasureSpec),
            resolveSize((76 * density).toInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = height / 2f
        paint.color = Color.rgb(43, 53, 58)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = Color.rgb(210, 226, 222)
        paint.textSize = 15 * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        val label = context.getString(R.string.rv3_dismiss)
        val available = width - handle - inset * 3
        while (paint.measureText(label) > available && paint.textSize > 10 * density) paint.textSize -= density
        canvas.drawText(label, handle + (width - handle) / 2f,
            height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
        val left = inset + fraction * travel
        paint.color = if (isEnabled) Color.rgb(189, 222, 211) else Color.GRAY
        rect.set(left, inset, left + handle, height - inset)
        canvas.drawRoundRect(rect, radius, radius, paint)
        // Plain chevron, drawn from geometry rather than any third-party asset.
        paint.color = Color.rgb(22, 50, 42)
        paint.strokeWidth = 3 * density
        val cx = left + handle / 2
        val cy = height / 2f
        canvas.drawLine(cx - 4 * density, cy - 8 * density, cx + 4 * density, cy, paint)
        canvas.drawLine(cx + 4 * density, cy, cx - 4 * density, cy + 8 * density, paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x > inset + handle || event.x < 0) return false
                downX = event.x
                pointer = event.getPointerId(0)
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val index = event.findPointerIndex(pointer)
                if (index < 0) { reset(); return true }
                fraction = ((event.getX(index) - downX) / travel).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val done = dragging && fraction >= 0.95f && event.x - downX > slop
                reset()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (done) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                reset()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return dragging
    }
    override fun performClick(): Boolean {
        if (!isEnabled) return false
        super.performClick()
        dismiss() // Also provides explicit TalkBack/keyboard activation without a drag requirement.
        return true
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.rv3_dismiss)))
    }
}
