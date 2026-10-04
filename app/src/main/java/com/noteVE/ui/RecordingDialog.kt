package com.noteVE.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.noteVE.R
import com.noteVE.domain.AudioRecorder
import com.noteVE.domain.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录音弹窗：顶部名称，中部大 R 角方形按钮（按住录音/松手暂停/再按继续），
 * 左下取消（删临时文件），右下插入（最终保存）。
 */
@Composable
fun RecordingDialog(
    tempFile: File,
    onInsert: (name: String) -> Unit,
    onCancel: () -> Unit
) {
    val recorder = remember { AudioRecorder() }
    var name by remember { mutableStateOf(defaultRecordingName()) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    var accumulatedMs by remember { mutableIntStateOf(0) }
    var sessionStart by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    var ticker by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current

    fun startRec() {
        try {
            recorder.startOrResume(tempFile)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.record_failed, Toast.LENGTH_SHORT).show()
            return
        }
        isRecording = true
        sessionStart = SystemClock.elapsedRealtime()
        ticker = scope.launch {
            while (isActive) {
                elapsedMs = accumulatedMs + (SystemClock.elapsedRealtime() - sessionStart).toInt()
                delay(100)
            }
        }
    }

    fun pauseRec() {
        recorder.pause()
        isRecording = false
        accumulatedMs += (SystemClock.elapsedRealtime() - sessionStart).toInt()
        ticker?.cancel()
    }

    DisposableEffect(Unit) {
        onDispose { recorder.stopAndRelease() }
    }

    AlertDialog(
        onDismissRequest = { recorder.stopAndRelease(); onCancel() },
        title = { Text(stringResource(R.string.insert_voice)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.recording_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    startRec()
                                    tryAwaitRelease()
                                    pauseRec()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(R.drawable.ic_voice_recording),
                                    stringResource(R.string.recording_hint),
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatDuration(elapsedMs),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_voice_mic),
                                stringResource(R.string.hold_to_record),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            stringResource(if (isRecording) R.string.recording_hint else R.string.hold_to_record),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = { recorder.stopAndRelease(); onCancel() }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    recorder.stopAndRelease()
                    if (tempFile.exists() && tempFile.length() > 0) onInsert(name)
                    else onCancel()
                }) {
                    Text(stringResource(R.string.insert))
                }
            }
        }
    )
}

private fun defaultRecordingName(): String {
    val fmt = SimpleDateFormat("MMdd_HHmm", Locale.getDefault())
    return "rec_${fmt.format(Date())}"
}
