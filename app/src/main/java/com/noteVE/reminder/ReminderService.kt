package com.noteVE.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.noteVE.R
import com.noteVE.domain.RepeatRule
import com.noteVE.domain.Settings
import com.noteVE.domain.Permissions
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One actor owns journal, notification slot, audio and all occurrence transitions. */
class ReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Command(val intent: Intent?, val startId: Int)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private lateinit var store: OccurrenceStore
    private lateinit var sound: ReminderSound
    private val rows = mutableListOf<Occurrence>()
    private var loaded = false
    private var foregroundId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var commandWakeLock: PowerManager.WakeLock? = null
    private var latestStartId = 0
    private val handler = Handler(Looper.getMainLooper())
    private val audioTimeout = Runnable { commands.trySend(Command(Intent(ACTION_MUTE_ALL), 0)) }
    private var audible = false // Session deadline is independent of preference toggles.
    private var soundActive = false
    private var vibrationActive = false
    private val manager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Ordinary apps cannot intercept KEYCODE_POWER globally. SCREEN_OFF is only silence.
            if (intent.action == Intent.ACTION_SCREEN_OFF) commands.trySend(Command(Intent(ACTION_MUTE_ALL), 0))
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = OccurrenceStore(this)
        sound = ReminderSound(this)
        NotificationHelper.createChannels(this)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            for (command in commands) {
                try {
                    acquireCommandWakeLock()
                    if (!loaded) restore()
                    execute(command.intent)
                    render()
                    if (rows.isEmpty() && commands.isEmpty) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        foregroundId = 0
                        stopSelfResult(latestStartId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reminder command failed; journal retained", e)
                    sound.stop(); ReminderVibrator.stop(); releaseWakeLock()
                    audible = false
                    soundActive = false
                    vibrationActive = false
                    handler.removeCallbacks(audioTimeout)
                    // In-memory transitions may not have committed. Reload the journal on retry.
                    loaded = false
                    presentationState.value = Presentation(false, emptyList())
                    state.value = emptyList()
                    if (rows.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundId = 0; stopSelfResult(latestStartId) }
                } finally {
                    commandWakeLock?.let { if (it.isHeld) runCatching { it.release() } }
                    commandWakeLock = null
                }
            }
        }
        scope.launch {
            combine(Settings.reminderMuted, Settings.reminderVibrationEnabled) { _, _ -> Unit }
                .collect {
                    // Only the actor touches rows/effects; never start an idle service for settings.
                    if (loaded) commands.trySend(Command(Intent(ACTION_PREFERENCES), 0))
                }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        // Must satisfy FGS deadline synchronously, before Room/journal I/O. No feedback yet.
        if (foregroundId == 0) {
            try {
                startForeground(BOOTSTRAP_ID, NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle(getString(R.string.rv3_loading))
                    .setOngoing(true).setSilent(true).build())
                foregroundId = BOOTSTRAP_ID
            } catch (e: Exception) {
                Log.e(TAG, "Foreground promotion denied", e)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        commands.trySend(Command(intent, startId))
        return START_STICKY
    }

    private suspend fun restore() {
        rows.clear()
        rows.addAll(store.load())
        for (row in rows.toList()) {
            if (store.consumed(row.noteId, row.expectedAt) || store.note(row.noteId) == null) {
                rows.remove(row); manager.cancel(row.notificationId); continue
            }
            if (row.phase == Occurrence.CLAIMED) {
                if (!store.advance(row)) { store.markConsumed(row); rows.remove(row); continue }
                replace(row.copy(phase = Occurrence.RINGING))
            }
            // A second repeat may already have advanced Room. Never re-register an older nextAt.
            val currentAt = store.note(row.noteId)?.reminderAt
            if (currentAt != null && currentAt > System.currentTimeMillis()) {
                ReminderScheduler(this).schedule(row.noteId, currentAt)
            }
            if (row.phase == Occurrence.SNOOZED) {
                if (row.resumeAt <= System.currentTimeMillis()) replace(row.copy(phase = Occurrence.RINGING, resumeAt = 0))
                else scheduleResume(row)
            }
        }
        store.save(rows)
        loaded = true
        publish()
    }

    private suspend fun execute(intent: Intent?) {
        val action = intent?.action ?: ACTION_FIRE
        val id = intent?.getLongExtra(EXTRA_NOTE_ID, -1) ?: -1
        val at = intent?.getLongExtra(EXTRA_EXPECTED_AT, -1) ?: -1
        when (action) {
            ACTION_MUTE, ACTION_MUTE_ALL -> {
                rows.toList().filter {
                    it.phase == Occurrence.RINGING &&
                        (action == ACTION_MUTE_ALL || (id > 0 && at >= 0 && it.noteId == id && it.expectedAt == at))
                }.forEach { replace(it.copy(phase = Occurrence.MUTED)) }
                store.save(rows)
            }
            ACTION_PREFERENCES -> Unit
            ACTION_DISMISS, ACTION_SNOOZE -> {
                // Tokens on UI/notification commands prevent old windows dismissing a new ring.
                val targets = rows.filter { it.noteId == id && at >= 0 && it.expectedAt == at &&
                    (action == ACTION_DISMISS || it.phase != Occurrence.SNOOZED) }
                for (row in targets) {
                    if (action == ACTION_DISMISS) {
                        store.markConsumed(row)
                        rows.remove(row)
                    } else {
                        val snoozed = row.copy(phase = Occurrence.SNOOZED, resumeAt = System.currentTimeMillis() + SNOOZE_MS)
                        replace(snoozed)
                    }
                }
                store.save(rows)
                targets.forEach { row ->
                    cancelResume(row)
                    if (action == ACTION_SNOOZE) rows.firstOrNull { it.key == row.key }?.let { scheduleResume(it) }
                }
            }
            ACTION_DISMISS_ALL -> {
                rows.forEach { store.markConsumed(it); cancelResume(it) }
                rows.clear(); store.save(rows)
            }
            ACTION_RESUME -> {
                val row = rows.firstOrNull { it.noteId == id && it.expectedAt == at } ?: return
                if (row.phase != Occurrence.SNOOZED || row.resumeAt > System.currentTimeMillis()) return
                if (store.note(id) == null) { store.markConsumed(row); rows.remove(row) }
                else replace(row.copy(phase = Occurrence.RINGING, resumeAt = 0))
                store.save(rows)
            }
            ACTION_RESTORE -> Unit
            ACTION_FIRE -> fire(id, at)
        }
        publish()
    }

    private suspend fun fire(id: Long, expectedAt: Long) {
        // Legacy/tokenless deliveries are intentionally inert. All v3 producers carry expectedAt.
        if (id <= 0 || expectedAt < 0) return
        if (rows.any { it.noteId == id && it.expectedAt == expectedAt }) return
        val note = store.note(id) ?: return
        val at = note.reminderAt ?: return
        if (expectedAt >= 0 && at != expectedAt) return
        if (at > System.currentTimeMillis() || store.consumed(id, at)) return
        val next = RepeatRule.nextOccurrenceAfter(at, note.repeatRule, System.currentTimeMillis())
        val row = Occurrence(id, at, note.title, note.repeatRule, next, store.allocateId())
        rows.add(row)
        store.save(rows) // write-ahead BEFORE SQL and before sound
        if (!store.advance(row)) {
            store.markConsumed(row); rows.remove(row); store.save(rows); return
        }
        replace(row.copy(phase = Occurrence.RINGING))
        store.save(rows)
        if (next != null) ReminderScheduler(this).schedule(id, next) else ReminderScheduler(this).cancel(id)
        ReminderTicker.refresh()
    }

    private fun replace(row: Occurrence) {
        val index = rows.indexOfFirst { it.key == row.key }
        if (index >= 0) rows[index] = row
    }

    private fun publish() {
        state.value = rows.map { RingingInfo(it.noteId, it.expectedAt, it.title, it.phase) }
        presentationState.value = Presentation(loaded, state.value)
    }

    private var postedIds = emptySet<Int>()
    private fun render() {
        val previous = foregroundId
        // Real FGS handoff BEFORE cancelling the old notification.
        val primary = rows.firstOrNull { it.phase == Occurrence.RINGING } ?: rows.firstOrNull()
        if (primary != null) {
            startForeground(primary.notificationId, notification(primary))
            foregroundId = primary.notificationId
            rows.filter { it.key != primary.key }.forEach { row ->
                runCatching { manager.notify(row.notificationId, notification(row)) }
            }
        }
        val current = rows.map { it.notificationId }.toSet()
        (postedIds - current).forEach { manager.cancel(it) }
        if (previous != 0 && previous !in current) manager.cancel(previous)
        postedIds = current
        val ringing = rows.any { it.phase == Occurrence.RINGING }
        if (ringing && !audible) {
            audible = true
            acquireWakeLock()
            // Preference changes must not reset this finite occurrence session deadline.
            handler.postDelayed(audioTimeout, MAX_RING_MS)
        } else if (!ringing && audible) {
            audible = false
            handler.removeCallbacks(audioTimeout)
            releaseWakeLock()
        }
        val shouldSound = ringing && !Settings.reminderMuted.value
        val shouldVibrate = ringing && Settings.reminderVibrationEnabled.value
        if (shouldSound != soundActive) {
            soundActive = shouldSound
            if (shouldSound) sound.start() else sound.stop()
        }
        if (shouldVibrate != vibrationActive) {
            vibrationActive = shouldVibrate
            if (shouldVibrate) ReminderVibrator.start(this) else ReminderVibrator.stop()
        }
    }

    private fun notification(row: Occurrence): Notification {
        val open = PendingIntent.getActivity(this, 0, ReminderActivity.intent(this, row.noteId, row.expectedAt), PI_FLAGS)
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(row.title.ifBlank { getString(R.string.notes) })
            .setContentText(getString(if (row.phase == Occurrence.SNOOZED) R.string.rv3_snoozed else if (row.phase == Occurrence.MUTED) R.string.rv3_muted else R.string.rv3_slide))
            .setContentIntent(open).setOngoing(true).setAutoCancel(false).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.rv3_mute), commandPi(row, ACTION_MUTE))
            .addAction(0, getString(R.string.rv3_dismiss), commandPi(row, ACTION_DISMISS))
            .addAction(0, getString(R.string.rv3_snooze), commandPi(row, ACTION_SNOOZE))
        // Official full-screen mechanism only. Unlocked/foreground behavior is the OS's choice.
        if (row.phase == Occurrence.RINGING && Permissions.fullScreenIntentGranted(this)) {
            builder.setFullScreenIntent(open, true)
        }
        return builder.build()
    }

    private fun commandPi(row: Occurrence, action: String): PendingIntent {
        val i = commandIntent(this, action, row.noteId, row.expectedAt)
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(this, 0, i, PI_FLAGS)
            else PendingIntent.getService(this, 0, i, PI_FLAGS)
    }

    private fun scheduleResume(row: Occurrence) {
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = commandPi(row, ACTION_RESUME)
        try {
            am.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(row.resumeAt,
                PendingIntent.getActivity(this, 0, ReminderActivity.intent(this, row.noteId, row.expectedAt), PI_FLAGS)), pi)
        } catch (e: Exception) {
            Log.w(TAG, "Snooze not exact; system may delay", e)
            runCatching { am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, row.resumeAt, pi) }
        }
        // In-process fallback; not a substitute for the system alarm.
        handler.postDelayed({ commands.trySend(Command(commandIntent(this, ACTION_RESUME, row.noteId, row.expectedAt), 0)) },
            (row.resumeAt - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private fun cancelResume(row: Occurrence) {
        runCatching { (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(commandPi(row, ACTION_RESUME)) }
    }

    private fun acquireCommandWakeLock() {
        runCatching {
            commandWakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noteVE:reminder-command").apply {
                    setReferenceCounted(false); acquire(30_000)
                }
        }.onFailure { Log.w(TAG, "Command wake lock unavailable", it) }
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        runCatching {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noteVE:reminder").apply {
                    setReferenceCounted(false); acquire(MAX_RING_MS + 5_000)
                }
        }.onFailure { Log.w(TAG, "Wake lock unavailable", it) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        sound.stop(); ReminderVibrator.stop(); releaseWakeLock()
        scope.cancel(); commands.close()
        state.value = emptyList()
        presentationState.value = Presentation(false, emptyList())
        commandWakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        commandWakeLock = null
        // Keep durable rows. START_STICKY/cold-start restore resumes the SAME occurrence.
        postedIds.forEach { manager.cancel(it) }
        super.onDestroy()
    }

    data class RingingInfo(val noteId: Long, val expectedAt: Long, val title: String, val phase: String)
    data class Presentation(val ready: Boolean, val rows: List<RingingInfo>)
    companion object {
        private const val TAG = "NoteReminder"
        private const val BOOTSTRAP_ID = 29_999
        private const val MAX_RING_MS = 10 * 60 * 1000L
        private const val SNOOZE_MS = 5 * 60 * 1000L
        private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_EXPECTED_AT = "expected_at"
        const val ACTION_FIRE = "com.noteVE.reminder.FIRE"
        const val ACTION_RESTORE = "com.noteVE.reminder.RESTORE"
        const val ACTION_DISMISS = "com.noteVE.reminder.DISMISS"
        const val ACTION_SNOOZE = "com.noteVE.reminder.SNOOZE"
        private const val ACTION_RESUME = "com.noteVE.reminder.RESUME"
        const val ACTION_MUTE = "com.noteVE.reminder.MUTE"
        private const val ACTION_MUTE_ALL = "com.noteVE.reminder.MUTE_ALL"
        private const val ACTION_PREFERENCES = "com.noteVE.reminder.PREFERENCES"
        private const val ACTION_DISMISS_ALL = "com.noteVE.reminder.DISMISS_ALL"
        private val state = MutableStateFlow<List<RingingInfo>>(emptyList())
        val activeOccurrences = state.asStateFlow()
        private val presentationState = MutableStateFlow(Presentation(false, emptyList()))
        val presentation = presentationState.asStateFlow()
        val hasActive: Boolean get() = state.value.isNotEmpty()
        fun isActive(noteId: Long): Boolean = state.value.any { it.noteId == noteId }

        fun commandIntent(context: Context, action: String, id: Long, at: Long): Intent =
            Intent(context, ReminderService::class.java).setAction(action)
                .setData(android.net.Uri.parse("noteve://command/$id/$at/${action.substringAfterLast('.') }"))
                .putExtra(EXTRA_NOTE_ID, id).putExtra(EXTRA_EXPECTED_AT, at)

        fun send(context: Context, action: String, id: Long, at: Long): Boolean =
            runCatching { ContextCompat.startForegroundService(context, commandIntent(context, action, id, at)) }
                .onFailure { Log.w(TAG, "Reminder command launch denied", it) }.isSuccess

        /** Legacy lifecycle calls are inert. Only explicit token-bearing controls may acknowledge. */
        @Deprecated("Use token-bearing send from an explicit user action")
        fun complete(context: Context, noteId: Long) = Unit
        @Deprecated("Opening/unlocking the app is not acknowledgment")
        fun completeAll(context: Context) = Unit

        /** Call from visible cold-start/reconcile; never blindly start media FGS from boot. */
        fun restoreIfNeeded(context: Context) {
            val prefs = context.getSharedPreferences("reminder_v3_occurrences", Context.MODE_PRIVATE)
            if (prefs.getString("active", "[]") != "[]") send(context, ACTION_RESTORE, -1, -1)
        }
    }
}
