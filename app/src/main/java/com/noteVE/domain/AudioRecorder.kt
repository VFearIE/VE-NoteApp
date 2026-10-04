package com.noteVE.domain

import android.media.MediaRecorder
import java.io.File

/** 录音器：按住录音，松手暂停（不保存），再按继续（resume 续录同一文件）。 */
class AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var started = false
    var isPaused = false
        private set

    /** 首次调用开始新录音；若处于暂停状态则继续录音。 */
    fun startOrResume(file: File) {
        if (recorder != null && started) {
            if (isPaused) {
                recorder?.resume()
                isPaused = false
            }
            return
        }
        file.parentFile?.mkdirs()
        val r = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
        }
        r.prepare()
        r.start()
        recorder = r
        started = true
        isPaused = false
    }

    /** 松手暂停，保留录音以便继续。 */
    fun pause() {
        val r = recorder ?: return
        if (started && !isPaused) {
            r.pause()
            isPaused = true
        }
    }

    /** 停止并释放（最终保存时调用）。 */
    fun stopAndRelease() {
        val r = recorder ?: return
        try { if (started) r.stop() } catch (_: Exception) {}
        r.release()
        recorder = null
        started = false
        isPaused = false
    }

    fun hasRecording(): Boolean = recorder != null && started
}
