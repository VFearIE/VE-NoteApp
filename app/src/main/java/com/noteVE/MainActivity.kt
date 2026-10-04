package com.noteVE

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.noteVE.domain.LocaleHelper
import com.noteVE.domain.Permissions
import com.noteVE.domain.Settings
import com.noteVE.reminder.ReminderService
import com.noteVE.reminder.ReminderVibrator

class MainActivity : ComponentActivity() {

    /** 本次启动要打开的笔记（来自提醒通知），-1 表示无。 */
    private var launchNoteId = -1L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.apply(newBase, Settings.currentLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // priv-app 静默自授；非 priv-app（二次分发）退回标准申请
        if (Permissions.isPrivApp(this)) {
            Permissions.selfGrant(this)
        } else {
            val missing = Permissions.missing(this)
            if (missing.isNotEmpty()) requestPermissions(missing, 1)
        }
        launchNoteId = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        setContent {
            NoteApp(initialNoteId = launchNoteId.takeIf { it > 0 })
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 应用已在前台时再次点通知：更新目标笔记并完成对应提醒
        setIntent(intent)
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (id > 0) {
            launchNoteId = id
            ReminderService.complete(this, id)
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户在提醒鸣响期间打开应用 → 视作已确认。
        // ★ 若本次是被某条提醒的通知拉起的，则**只完成那一条**（不影响其它并发提醒）；
        //   否则视为用户主动打开应用，完成全部。
        if (!ReminderService.hasActive) {
            // 无条件兜底停震动：防止进程重建等导致静态标志错位、马达停不下来
            ReminderVibrator.stop()
            return
        }
        if (launchNoteId > 0 && ReminderService.isActive(launchNoteId)) {
            ReminderService.complete(this, launchNoteId)
        } else {
            ReminderService.completeAll(this)
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
