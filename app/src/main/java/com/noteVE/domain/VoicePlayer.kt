package com.noteVE.domain

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** 时长格式化：0:02 / 1:45 */
fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    return "${totalSec / 60}:${String.format("%02d", totalSec % 60)}"
}

/**
 * 单一录音播放器（同一时刻只播一个）。
 * 暴露 Compose 状态：播放中/进度/时长，60fps ticker 刷新进度。
 */
class VoicePlayer(private val scope: CoroutineScope) {

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    var playingName: String by mutableStateOf("")
        private set
    var isPlaying: Boolean by mutableStateOf(false)
        private set
    var positionMs: Int by mutableIntStateOf(0)
        private set
    var durationMs: Int by mutableIntStateOf(0)
        private set

    fun toggle(file: File, name: String) {
        if (playingName == name) {
            if (isPlaying) pause() else resume()
            return
        }
        stop()
        val mp = MediaPlayer()
        mp.setOnPreparedListener {
            durationMs = it.duration
            it.start()
            isPlaying = true
            startTicker()
        }
        mp.setOnCompletionListener { onEnd() }
        mp.setOnErrorListener { _, _, _ -> onEnd(); true }
        mp.setDataSource(file.absolutePath)
        mp.prepareAsync()
        player = mp
        playingName = name
        positionMs = 0
    }

    fun stop() {
        stopTicker()
        player?.release()
        player = null
        playingName = ""
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }

    fun isPlaying(name: String): Boolean = playingName == name && isPlaying

    private fun pause() {
        player?.pause()
        isPlaying = false
        stopTicker()
    }

    private fun resume() {
        player?.start()
        isPlaying = true
        startTicker()
    }

    private fun onEnd() {
        isPlaying = false
        positionMs = durationMs
        stopTicker()
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                if (isPlaying) positionMs = p.currentPosition
                delay(16) // ~60fps
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
