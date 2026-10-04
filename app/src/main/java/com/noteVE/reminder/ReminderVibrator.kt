package com.noteVE.reminder

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 提醒震动控制（单例，唯一震动源）。
 *
 * ★ 为什么不用 `Vibrator.vibrate(pattern, 0)` 无限循环：
 *   实测该 ROM 上「无限循环模式」在另一个 Vibrator 实例上 `cancel()` **无法停止**，
 *   曾导致提醒完成后仍持续震动。
 *
 * 改用「有限震动 + Handler 自重复」：
 *   - 每次震一个有限 pattern（约 2.4s），由 Handler 循环重触发
 *   - `stop()` 既移除回调又 `cancel()`，一定停得下来
 *
 * 单例持有 Vibrator，保证 start/stop 操作同一对象。
 */
object ReminderVibrator {

    private const val TAG = "NoteReminder"

    /**
     * 单次震动 pattern：立即开始 → 震800ms → 停400ms → 震800ms → 停400ms
     * 总时长约 2.4s（有限，非无限循环）。
     */
    private val PATTERN = longArrayOf(0, 800, 400, 800)

    /** 两次 pattern 之间的间隔（pattern 本身 2.4s，所以稍长一点）。 */
    private const val INTERVAL_MS = 2600L

    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val repeatTask = object : Runnable {
        override fun run() {
            if (!running) return
            buzz()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun obtain(context: Context): Vibrator? {
        vibrator?.let { return it }
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        Log.i(TAG, "obtain Vibrator: " + (v != null))
        return v
    }

    private fun buzz() {
        val v = vibrator ?: return
        var ok = false
        if (Build.VERSION.SDK_INT >= 26) {
            ok = runCatching {
                v.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
            }.isSuccess
        } else {
            @Suppress("DEPRECATION")
            ok = runCatching { v.vibrate(PATTERN, -1) }.isSuccess
        }
        Log.i(TAG, "buzz: ok=" + ok)
    }

    /** 开始循环震动。 */
    fun start(context: Context) {
        if (running) { Log.i(TAG, "start: 已在运行，忽略"); return }
        val v = obtain(context)
        if (v == null) { Log.i(TAG, "!! start: 拿不到 Vibrator"); return }
        running = true
        runCatching { v.cancel() }
        buzz()
        handler.postDelayed(repeatTask, INTERVAL_MS)
        Log.i(TAG, "start: 循环震动已启动")
    }

    /** 停止震动（幂等）。 */
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(repeatTask)
        runCatching { vibrator?.cancel() }
        Log.i(TAG, "stop: 震动已停止")
    }

    /** 释放（进程结束/服务销毁时）。 */
    fun release() {
        stop()
        vibrator = null
    }
}
