package com.noteVE.reminder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noteVE.MainActivity
import com.noteVE.R
import com.noteVE.domain.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 提醒前台服务：停驻通知 + 持续震动。
 *
 * ## 谁启动它
 * - **主路径**：AlarmManager 的 `PendingIntent.getForegroundService`（系统作发起方，
 *   不受 Android 9「后台不能启动前台服务」限制）
 * - 冗余：[ReminderTicker] 进程内到点调用
 * - 低版本：[ReminderTrigger] 广播中转
 *
 * ## ★ 多提醒隔离（v2.1.0 重构要点）
 *
 * 旧实现用单一全局态（`activeNoteId` / `activeTitle` / 固定 `NOTIF_ID`），
 * 当提醒 A、B 接近同时触发时会出现：
 * - B 覆盖 A 的通知（同一 NOTIF_ID）
 * - B 覆盖 A 的身份（`activeNoteId` 被改写）
 * - A 完成时误把 B 判为完成（甚至 `stopService` 直接把 B 也杀掉）
 *
 * 现改为**按 noteId 隔离**：
 * - 每条提醒独立登记在 [actives]（`noteId → title`）
 * - 每条提醒有**稳定且唯一**的通知 ID（[ongoingIdFor] / [doneIdFor]）
 * - A 完成只影响 A；仅当**所有**提醒都结束才停服务
 * - 前台槽位（`startForeground`）只能有一个：首条占据，其余走普通 `notify()`
 *   （同为 `setOngoing(true)`，用户同样划不掉）；前台主完成时自动把下一条升为前台
 * - 上下文全部来自 Intent 的 `noteId` + 数据库查询，**不依赖进程内残留状态**，
 *   故服务被系统重建后仍能正确恢复
 *
 * ## 提醒生命周期
 * 触发 → 停驻通知 + 震动 → 完成条件：
 *   ① 用户点击该条通知进入笔记 → 只完成**该条**
 *   ② 息屏持续超过 [SCREEN_OFF_GRACE_MS] → 完成**全部**（用户已离开）
 *   ③ 解锁设备 → 完成**全部**（用户已回来）
 * → 该条转为「已完成」可划走通知；全部完成则停服务、停震动
 */
class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val graceRunnable = Runnable {
        if (actives.isNotEmpty()) {
            log("息屏超过宽限期 → 完成全部提醒（${actives.size} 条）")
            completeAll(this)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(graceRunnable)
                    handler.postDelayed(graceRunnable, SCREEN_OFF_GRACE_MS)
                    log("息屏，启动 ${SCREEN_OFF_GRACE_MS}ms 宽限计时")
                }
                Intent.ACTION_SCREEN_ON -> {
                    handler.removeCallbacks(graceRunnable)
                    log("亮屏，取消完成判定")
                }
                Intent.ACTION_USER_PRESENT -> {
                    handler.removeCallbacks(graceRunnable)
                    if (actives.isNotEmpty()) {
                        log("解锁 → 完成全部提醒（${actives.size} 条）")
                        completeAll(context)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val noteId = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        log("onStartCommand noteId=$noteId startId=$startId 活跃=${actives.size}")
        if (noteId <= 0) {
            if (actives.isEmpty()) stopSelf(startId)
            return START_NOT_STICKY
        }

        // 去重：同一条已在提醒中（AlarmManager 与进程内计时器可能同时触发）。
        // ★ 不能 stopSelf()（会停掉前台服务，致通知降级、震动中断），直接返回即可。
        if (actives.containsKey(noteId)) {
            log("重复触发，忽略 noteId=$noteId")
            return START_NOT_STICKY
        }

        // 登记这条提醒
        actives[noteId] = ""

        // 通知策略：第一条占据前台槽位，其余走普通 notify（同为 ongoing，划不掉）
        val notifId = ongoingIdFor(noteId)
        if (foregroundNoteId == 0L) {
            try {
                startForeground(notifId, buildOngoingNotification(this, noteId, ""))
                foregroundNoteId = noteId
                log("startForeground OK noteId=$noteId id=$notifId")
            } catch (t: Throwable) {
                log("!! startForeground 失败: $t")
                actives.remove(noteId)
                if (actives.isEmpty()) stopSelf(startId)
                return START_NOT_STICKY
            }
        } else {
            runCatching {
                NotificationManagerCompat.from(this)
                    .notify(notifId, buildOngoingNotification(this, noteId, ""))
            }
            log("附加通知 noteId=$noteId id=$notifId（前台槽位归 $foregroundNoteId）")
        }

        // 持续震动（多条提醒共用一路震动，只要还有活跃提醒就继续）
        ReminderVibrator.start(this)
        log("震动已启动")

        // 监听息屏/亮屏/解锁（重复注册是幂等的）
        if (!receiverRegistered) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            runCatching { registerReceiver(screenReceiver, f) }
            receiverRegistered = true
        }

        // 异步：查标题回填通知 + 推进该条的重复规则
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val note = runCatching { NoteRepository(app).getNoteForReminder(noteId) }.getOrNull()
            val title = note?.title ?: ""
            if (actives.containsKey(noteId)) {
                actives[noteId] = title
                if (title.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        runCatching {
                            NotificationManagerCompat.from(this@ReminderService)
                                .notify(notifId, buildOngoingNotification(this@ReminderService, noteId, title))
                        }
                    }
                }
            }
            if (note != null) {
                runCatching {
                    val repo = NoteRepository(app)
                    val next = repo.nextRepeat(note.reminderAt ?: 0L, note.repeatRule)
                    if (next != null && next > System.currentTimeMillis()) {
                        repo.updateReminder(noteId, next, note.repeatRule)
                        log("重复提醒 → 为 noteId=$noteId 排下一次")
                    } else {
                        repo.updateReminder(noteId, null, null)
                        log("一次性提醒 → 已清除 noteId=$noteId")
                    }
                }
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        log("onDestroy（剩余活跃 ${actives.size}）")
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        handler.removeCallbacks(graceRunnable)
        // ★ 普通通知不会随服务销毁而消失，必须显式撤销，否则会残留成「僵尸提醒」
        for (id in actives.keys.toList()) {
            runCatching { NotificationManagerCompat.from(this).cancel(ongoingIdFor(id)) }
        }
        actives.clear()
        foregroundNoteId = 0L
        ReminderVibrator.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NoteReminder"

        /** 息屏宽限期：超过此时长才算「用户已离开」，避免误判完成。 */
        private const val SCREEN_OFF_GRACE_MS = 8_000L

        /** 通知 ID 段位：进行中与已完成分开，避免两条提醒的 ID 互相顶掉。 */
        private const val ONGOING_BASE = 2_000
        private const val DONE_BASE = 200_000
        private const val ID_RANGE = 100_000L

        const val EXTRA_NOTE_ID = "note_id"

        /** 活跃提醒：noteId → 标题。按 noteId 隔离，杜绝单实例状态互相覆盖。 */
        private val actives = ConcurrentHashMap<Long, String>()

        /** 当前占据前台通知槽位的 noteId（0 表示无）。前台槽位全服务仅一个。 */
        @Volatile
        private var foregroundNoteId = 0L

        @Volatile
        private var receiverRegistered = false

        /** 稳定且唯一的通知 ID（同一 noteId 永远得到同一值，跨进程重建也不变）。 */
        fun ongoingIdFor(noteId: Long): Int = (ONGOING_BASE + noteId % ID_RANGE).toInt()

        /** 「提醒已完成」通知的 ID（与进行中错开，避免互相覆盖）。 */
        fun doneIdFor(noteId: Long): Int = (DONE_BASE + noteId % ID_RANGE).toInt()

        /** 是否存在任何活跃提醒（供 MainActivity 判断是否需兜底停震动）。 */
        val hasActive: Boolean get() = actives.isNotEmpty()

        /** 指定笔记是否正在提醒中。 */
        fun isActive(noteId: Long): Boolean = actives.containsKey(noteId)

        /**
         * 完成**指定**提醒（用户点击该条通知 / 打开对应笔记）。
         * 只影响这一条；仅当没有其它活跃提醒时才停服务。
         */
        fun complete(context: Context, noteId: Long) {
            if (noteId <= 0) return
            val title = actives.remove(noteId) ?: return
            log("完成提醒 noteId=$noteId title=$title")
            postDoneNotification(context, noteId, title)
            runCatching { NotificationManagerCompat.from(context).cancel(ongoingIdFor(noteId)) }
            promoteForegroundIfNeeded(context)
            stopIfIdle(context)
        }

        /**
         * 完成**全部**活跃提醒（息屏宽限到期 / 解锁 / 用户打开应用）。
         * 每条各自转「已完成」通知，互不干扰；结束后停服务。
         */
        fun completeAll(context: Context) {
            val ids = actives.keys.toList()
            if (ids.isEmpty()) return
            for (id in ids) {
                val title = actives.remove(id) ?: continue
                postDoneNotification(context, id, title)
                runCatching { NotificationManagerCompat.from(context).cancel(ongoingIdFor(id)) }
            }
            foregroundNoteId = 0L
            ReminderVibrator.stop()
            runCatching { context.stopService(Intent(context, ReminderService::class.java)) }
        }

        /** 前台主完成后，把剩余任一条提升为前台（否则服务会失去前台身份）。 */
        private fun promoteForegroundIfNeeded(context: Context) {
            if (foregroundNoteId == 0L || actives.containsKey(foregroundNoteId)) return
            val next = actives.keys.firstOrNull() ?: run {
                foregroundNoteId = 0L
                return
            }
            val title = actives[next] ?: ""
            runCatching {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                mgr.notify(ongoingIdFor(next), buildOngoingNotification(context, next, title))
            }
            foregroundNoteId = next
            log("前台槽位移交 noteId=$next")
        }

        /** 没有任何活跃提醒时停服务、停震动。 */
        private fun stopIfIdle(context: Context) {
            if (actives.isNotEmpty()) return
            foregroundNoteId = 0L
            ReminderVibrator.stop()
            runCatching { context.stopService(Intent(context, ReminderService::class.java)) }
        }

        /** 发布「提醒已完成」通知（可划走，点击回到对应笔记）。 */
        private fun postDoneNotification(context: Context, noteId: Long, title: String) {
            NotificationHelper.createChannels(context)
            val open = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, noteId.toInt(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    context.getString(
                        R.string.reminder_done,
                        title.ifBlank { context.getString(R.string.notes) }
                    )
                )
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            runCatching {
                NotificationManagerCompat.from(context).notify(doneIdFor(noteId), n)
            }
        }

        private fun log(msg: String) = Log.i(TAG, msg)
    }
}

/** 停驻通知构建。 */
private fun buildOngoingNotification(context: Context, noteId: Long, title: String): Notification {
    NotificationHelper.createChannels(context)
    val open = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    // ★ requestCode 用 noteId：每条提醒的 PendingIntent 相互独立
    val pi = PendingIntent.getActivity(
        context, noteId.toInt(), open,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title.ifBlank { context.getString(R.string.notes) })
        .setContentText(
            context.getString(R.string.event_reminder) + " · " +
                context.getString(R.string.click_to_open) + " · " +
                context.getString(R.string.screen_off_to_dismiss)
        )
        .setContentIntent(pi)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()
}
