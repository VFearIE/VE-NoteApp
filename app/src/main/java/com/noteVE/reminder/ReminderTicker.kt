package com.noteVE.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.noteVE.data.Note
import com.noteVE.domain.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 进程内提醒计时器（**冗余备份通道**）。
 *
 * ## 定位
 *
 * 准点提醒的主路径是 [ReminderScheduler] 的 AlarmManager —— 只有它能**唤醒深睡设备**
 * （`RTC_WAKEUP`）。`delay()` 基于单调时钟（uptimeMillis，不含深睡），设备睡着时不会到点。
 *
 * 本计时器的价值：进程恰好活着时，绕过系统调度直接触发，作为兜底。
 *
 * ## 性能
 *
 * **不是轮询**：算出距下一条提醒的毫秒数直接 `delay()` 挂起（不占 CPU，空闲 0 唤醒）；
 * 提醒增删改时经 [refresh] 发信号立即打断重算。
 * 对比 5 秒轮询的 17280 次/日唤醒，本方案 ≈0。
 *
 * ## 时间变化
 *
 * 监听 `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED` → 重算，与 AlarmManager 行为对齐。
 */
object ReminderTicker {

    private const val TAG = "NoteReminder"
    private const val IDLE_SLEEP_MS = 12 * 60 * 60 * 1000L

    private val scope = CoroutineScope(Dispatchers.IO)
    private val signal = Channel<Unit>(Channel.CONFLATED)

    private var job: Job? = null
    private var timeReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    /** 进程启动时调用；重复调用安全。 */
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        appContext = app
        registerTimeChangeReceiver(app)

        job = scope.launch {
            val repo = NoteRepository(app)
            while (isActive) {
                val next = try {
                    repo.nextDueReminder()
                } catch (t: Throwable) {
                    Log.w(TAG, "查询提醒失败", t); null
                }

                if (next == null) {
                    withTimeoutOrNull(IDLE_SLEEP_MS) { signal.receive() }
                    continue
                }

                val at = next.reminderAt
                if (at == null) { delay(1000); continue }

                val waitMs = at - System.currentTimeMillis()
                if (waitMs <= 0) {
                    fire(app, next)
                    continue
                }
                // 精确挂起；数据变更/时间变化会打断，到点则超时返回
                withTimeoutOrNull(waitMs) { signal.receive() }
            }
        }
    }

    /** 提醒数据变更后调用：立刻重算。 */
    fun refresh() {
        runCatching { signal.trySend(Unit) }
    }

    fun stop() {
        job?.cancel(); job = null
        timeReceiver?.let { runCatching { appContext?.unregisterReceiver(it) } }
        timeReceiver = null
    }

    // ---------------- 内部 ----------------

    private suspend fun fire(context: Context, note: Note) {
        Log.i(TAG, "计时器触发提醒 note=${note.id}")
        // 与服务内部去重配合：同一条已在提醒中会被忽略
        val svc = Intent(context, ReminderService::class.java).apply {
            putExtra(ReminderService.EXTRA_NOTE_ID, note.id)
        }
        runCatching { ContextCompat.startForegroundService(context, svc) }
            .onFailure { Log.w(TAG, "计时器启动服务失败(应用可能在后台): $it") }
        delay(600)
    }

    private fun registerTimeChangeReceiver(context: Context) {
        if (timeReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                Log.i(TAG, "时间/时区变化 → 重算提醒")
                refresh()
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(context, r, f, ContextCompat.RECEIVER_NOT_EXPORTED) }
        timeReceiver = r
    }
}
