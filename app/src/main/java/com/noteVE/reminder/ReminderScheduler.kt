package com.noteVE.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/** AlarmManager is primary; a foreground-service PI still needs platform launch exemptions. */
class ReminderScheduler(private val context: Context) {
    enum class Result { EXACT, INEXACT, FAILED, PAST }
    private val am get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs get() = context.getSharedPreferences("reminder_v3_schedule", Context.MODE_PRIVATE)

    // Kept Unit-returning for the old Repository/Ticker callers.
    fun schedule(noteId: Long, triggerAt: Long) { scheduleChecked(noteId, triggerAt) }

    fun scheduleChecked(noteId: Long, triggerAt: Long): Result {
        if (triggerAt <= System.currentTimeMillis()) {
            ReminderTicker.refresh()
            return Result.PAST
        }
        val previous = prefs.getLong("at_$noteId", 0)
        if (previous != 0L && previous != triggerAt) cancelAt(noteId, previous)
        val pi = pendingIntent(noteId, triggerAt)
        val result = try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(noteId, triggerAt)), pi)
            Result.EXACT
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Result.EXACT
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Result.INEXACT
                }
            } catch (fallback: Exception) {
                Log.w("NoteReminder", "Alarm registration failed", fallback)
                Result.FAILED
            }
        }
        prefs.edit().putLong("at_$noteId", triggerAt).putString("health_$noteId", result.name).apply()
        ReminderTicker.refresh()
        return result
    }

    fun status(noteId: Long): String? = prefs.getString("health_$noteId", null)

    fun cancel(noteId: Long) {
        val at = prefs.getLong("at_$noteId", 0)
        if (at != 0L) cancelAt(noteId, at)
        prefs.edit().remove("at_$noteId").remove("health_$noteId").apply()
        // Cancel pre-v3 identity too. Its tokenless deliveries are validated by the service.
        runCatching {
            val legacy = Intent(context, if (Build.VERSION.SDK_INT >= 26) ReminderService::class.java else ReminderTrigger::class.java)
                .setData(Uri.parse("note://$noteId"))
            if (Build.VERSION.SDK_INT < 26) legacy.action = ReminderTrigger.ACTION_REMIND
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            val pi = if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, noteId.toInt(), legacy, flags)
                else PendingIntent.getBroadcast(context, noteId.toInt(), legacy, flags)
            pi?.let { am.cancel(it); it.cancel() }
        }
        ReminderTicker.refresh()
    }

    private fun cancelAt(id: Long, at: Long) {
        runCatching { pendingIntent(id, at).let { am.cancel(it); it.cancel() } }
    }

    private fun pendingIntent(id: Long, at: Long): PendingIntent {
        val i = Intent(context, if (Build.VERSION.SDK_INT >= 26) ReminderService::class.java else ReminderTrigger::class.java)
            .setData(Uri.parse("noteve://alarm/$id/$at"))
            .putExtra(ReminderService.EXTRA_NOTE_ID, id)
            .putExtra(ReminderService.EXTRA_EXPECTED_AT, at)
        i.action = if (Build.VERSION.SDK_INT >= 26) ReminderService.ACTION_FIRE else ReminderTrigger.ACTION_REMIND
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 0, i, flags)
            else PendingIntent.getBroadcast(context, 0, i, flags)
    }

    private fun showIntent(id: Long, at: Long): PendingIntent = PendingIntent.getActivity(
        context, 0, ReminderActivity.intent(context, id, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
