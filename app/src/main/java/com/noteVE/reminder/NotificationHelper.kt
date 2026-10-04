package com.noteVE.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noteVE.MainActivity
import com.noteVE.R
import com.noteVE.data.Note

object NotificationHelper {

    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_BACKUP = "backup"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_MAX).apply {
                setShowBadge(true)
                enableVibration(false)         // ★ 关掉渠道震动：震动统一由 ReminderVibrator 控制，否则 cancel() 盖不住
                setBypassDnd(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BACKUP, context.getString(R.string.backup_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun showReminder(context: Context, note: Note) {
        if (!canPost(context)) return
        val title = note.title.ifBlank { context.getString(R.string.notes) }
        val body = note.plainText.ifBlank { context.getString(R.string.body_hint) }

        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, note.id.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(note.id.toInt(), n)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
