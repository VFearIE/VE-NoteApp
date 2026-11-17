package com.noteVE.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 提醒广播入口（**仅 API 26 以下的兜底**）。
 *
 * API 26+ 走 [ReminderScheduler] 的 `PendingIntent.getForegroundService` ——
 * 由系统直接拉起前台服务；仍受平台精确闹钟豁免与后台启动规则约束。
 * 低版本没有该 API，只能经广播中转。
 *
 * 逻辑极简：收到广播后拉起 [ReminderService]，其余全部由服务自己处理
 * （查库、停驻通知、震动、推进重复规则）。
 */
class ReminderTrigger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val at = intent.getLongExtra(ReminderService.EXTRA_EXPECTED_AT, -1)
        if (noteId <= 0 || at < 0) return
        Log.i("NoteReminder", "广播触发(低版本兜底) noteId=$noteId")
        val svc = ReminderService.commandIntent(context, ReminderService.ACTION_FIRE, noteId, at)
        runCatching { ContextCompat.startForegroundService(context, svc) }
            .onFailure { Log.w("NoteReminder", "拉起服务失败: $it") }
    }

    companion object {
        const val ACTION_REMIND = "com.noteVE.action.REMIND"
        const val EXTRA_NOTE_ID = "note_id"
    }
}
