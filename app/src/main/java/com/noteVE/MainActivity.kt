package com.noteVE

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.noteVE.domain.LocaleHelper
import com.noteVE.domain.Permissions
import com.noteVE.domain.Settings
import com.noteVE.reminder.ReminderService
import com.noteVE.reminder.ReminderVibrator
import com.noteVE.ui.NoteApp

class MainActivity : ComponentActivity() {

    /** 本次启动要打开的笔记（来自提醒通知），-1 表示无。 */
    private var launchNoteId by mutableStateOf(-1L)
    private var launchSequence by mutableStateOf(0L)

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
            NoteApp(initialNoteId = launchNoteId.takeIf { it > 0 }, launchSequence = launchSequence)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 导航事件独立于响铃确认；打开应用不再误完成提醒。
        setIntent(intent)
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (id > 0) {
            launchNoteId = id
            launchSequence++
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
