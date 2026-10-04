package com.noteVE.backup

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.noteVE.R
import com.noteVE.domain.NoteRepository
import com.noteVE.reminder.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 导出/导入前台服务。 */
class BackupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val uri: Uri? = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_URI, Uri::class.java) }
        val noteId = intent?.getLongExtra(EXTRA_NOTE_ID, 0L) ?: 0L
        val receiver: ResultReceiver? = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT, ResultReceiver::class.java) }

        if (action == null || uri == null) { stopSelf(startId); return START_NOT_STICKY }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.backup_running)))
        CoroutineScope(Dispatchers.IO).launch {
            val repo = NoteRepository(this@BackupService)
            val (code, msg) = try {
                when (action) {
                    ACTION_EXPORT_SINGLE -> {
                        val ok = contentResolver.openOutputStream(uri)!!.use { repo.exportSingle(noteId, it) }
                        if (ok) R_OK to getString(R.string.export_done) else R_ERROR to getString(R.string.export_failed)
                    }
                    ACTION_EXPORT_FULL -> {
                        contentResolver.openOutputStream(uri)!!.use { repo.exportFull(it) }
                        R_OK to getString(R.string.export_done)
                    }
                    ACTION_IMPORT_SINGLE, ACTION_IMPORT_FULL -> {
                        // 单/全量入口合并：自动识别包类型
                        val r = contentResolver.openInputStream(uri)!!.use { repo.importAuto(it) }
                        when (r) {
                            is NoteRepository.ImportResult.Imported ->
                                R_OK to getString(R.string.imported_count, r.added, r.skipped)
                            else -> R_INVALID to ""
                        }
                    }
                    else -> R_ERROR to getString(R.string.export_failed)
                }
            } catch (t: Throwable) {
                R_ERROR to getString(R.string.export_failed)
            }
            receiver?.send(code, Bundle().apply { putString(EXTRA_MSG, msg) })
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String): Notification {
        NotificationHelper.createChannels(this)
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_EXPORT_SINGLE = "com.noteVE.action.EXPORT_SINGLE"
        const val ACTION_EXPORT_FULL = "com.noteVE.action.EXPORT_FULL"
        const val ACTION_IMPORT_SINGLE = "com.noteVE.action.IMPORT_SINGLE"
        const val ACTION_IMPORT_FULL = "com.noteVE.action.IMPORT_FULL"
        const val EXTRA_URI = "uri"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_RESULT = "result"
        const val EXTRA_MSG = "message"
        const val R_OK = 1
        const val R_ERROR = 0
        const val R_INVALID = 4
        private const val NOTIFICATION_ID = 1001
    }
}
