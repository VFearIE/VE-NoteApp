package com.noteVE

import android.app.Application
import com.noteVE.domain.NoteRepository
import com.noteVE.domain.Permissions
import com.noteVE.domain.Settings
import com.noteVE.reminder.NotificationHelper
import com.noteVE.reminder.ReminderService
import com.noteVE.reminder.ReminderTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** System alarms own delivery; the process ticker is only a fallback. */
class NoteApplication : Application() {
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        NotificationHelper.createChannels(this)
        if (Permissions.isPrivApp(this)) Permissions.selfGrant(this)
        ReminderTicker.start(this)
        recoveryScope.launch {
            runCatching { ReminderService.restoreIfNeeded(this@NoteApplication) }
            runCatching { NoteRepository(this@NoteApplication).rescheduleAllReminders() }
                .onFailure { android.util.Log.e("NoteReminder", "Alarm recovery failed", it) }
        }
    }
}
