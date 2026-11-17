package com.noteVE.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/** Service-owned looping alarm stream. Never changes global volume or bypasses DND. */
internal class ReminderSound(private val context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wanted = false
    private var prepared = false
    private var focusGranted = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusGranted = false
                if (prepared) runCatching { player?.pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                if (wanted && prepared) runCatching { player?.start() }
            }
        }
    }

    fun start() {
        if (wanted) return
        wanted = true
        val granted = try { if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
            focus = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        } catch (e: Exception) {
            Log.w("NoteReminder", "Audio focus unavailable", e)
            stop(); return
        }
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return }
        focusGranted = true
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(attributes)
            p.setDataSource(context, uri)
            p.isLooping = true
            p.setOnPreparedListener {
                if (wanted && player === it) {
                    prepared = true
                    if (focusGranted) runCatching { it.start() }.onFailure { stop() }
                }
            }
            p.setOnErrorListener { _, what, extra ->
                Log.w("NoteReminder", "Alarm audio error $what/$extra")
                stop(); true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            Log.w("NoteReminder", "Alarm audio unavailable; vibration remains", e)
            stop()
        }
    }

    fun stop() {
        wanted = false
        prepared = false
        focusGranted = false
        player?.let { runCatching { it.release() } }
        player = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { manager.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
        focus = null
    }
}
