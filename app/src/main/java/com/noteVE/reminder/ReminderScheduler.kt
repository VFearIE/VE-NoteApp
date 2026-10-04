package com.noteVE.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 提醒调度器（**唯一调度入口**）。
 *
 * ## 双通道
 *
 * | 通道 | 适用 | 说明 |
 * |---|---|---|
 * | ① AlarmManager | **主路径** | 只有 `RTC_WAKEUP` 能唤醒深睡设备；由系统托管，进程死了也触发 |
 * | ② 进程内计时器（[ReminderTicker]） | 冗余 | `delay()` 基于单调时钟，深睡期间不到点 |
 *
 * ## ★ 为什么用 PendingIntent.getForegroundService 而不是 getBroadcast
 *
 * Android 9 起限制：**后台应用不能启动前台服务**（豁免列表仅含 `BOOT_COMPLETED`
 * 等系统广播，自定义广播不在其中）。
 *
 * 初版用 `getBroadcast` → 广播接收器再 `startForegroundService`，
 * 结果被系统拒绝（异常还被 `runCatching` 吞掉，表现为「只弹通知、无震动」）。
 *
 * 改为 `getForegroundService` 后，**由系统（AlarmManager）直接启动前台服务**，
 * 系统是发起方 → 不受后台限制 → 服务必定起来，震动/停驻通知都正常。
 *
 * 注：`getForegroundService` 需 API 26+；低版本降级为 `getBroadcast`（走 [ReminderTrigger]）。
 */
class ReminderScheduler(private val context: Context) {

    private val am get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 调度一条提醒。 */
    fun schedule(noteId: Long, triggerAt: Long) {
        if (triggerAt <= System.currentTimeMillis()) return
        setSystemAlarm(noteId, triggerAt)
    }

    /** 取消一条提醒。 */
    fun cancel(noteId: Long) {
        runCatching { am.cancel(pendingIntent(noteId)) }
        ReminderTicker.refresh()
    }

    // ---------------- AlarmManager 通道 ----------------

    private fun setSystemAlarm(noteId: Long, triggerAt: Long) {
        val pi = pendingIntent(noteId)
        // 首选 setAlarmClock：与系统闹钟同级，Doze 不推迟，并在系统显示「下一个闹钟」。
        val shown = runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
            true
        }.getOrDefault(false)
        if (shown) return
        if (canExact()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /**
     * 构造 PendingIntent：API 26+ 直接拉起前台服务；低版本走广播中转。
     * 只携带 noteId —— 标题等由服务自己查库获取。
     */
    private fun pendingIntent(noteId: Long): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) {
            val intent = Intent(context, ReminderService::class.java).apply {
                data = Uri.parse("note://$noteId")
                putExtra(ReminderService.EXTRA_NOTE_ID, noteId)
            }
            PendingIntent.getForegroundService(context, noteId.toInt(), intent, flags)
        } else {
            val intent = Intent(context, ReminderTrigger::class.java).apply {
                action = ReminderTrigger.ACTION_REMIND
                data = Uri.parse("note://$noteId")
                putExtra(ReminderTrigger.EXTRA_NOTE_ID, noteId)
            }
            PendingIntent.getBroadcast(context, noteId.toInt(), intent, flags)
        }
    }

    private fun canExact(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
}
