package com.noteVE.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.noteVE.R
import com.noteVE.backup.BackupService

internal enum class BackupPhase { PREPARING, RUNNING, SUCCESS, FAILURE }

/** Screen-owned state survives configuration changes; each launch has its own receiver. */
internal class BackupProgressState : ViewModel() {
    var phase by mutableStateOf<BackupPhase?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var generation = 0L

    fun prepare() {
        generation++
        message = null
        phase = BackupPhase.PREPARING
    }

    fun fail() { message = null; phase = BackupPhase.FAILURE }
    fun dismiss() { phase = null; message = null }

    fun start(context: Context, launch: (ResultReceiver) -> Unit) {
        prepare() // Visible immediately, even before the service acknowledges the request.
        val token = generation
        val app = context.applicationContext
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (token != generation || phase == null) return
                when (resultCode) {
                    BackupService.R_STARTED -> if (phase == BackupPhase.PREPARING) phase = BackupPhase.RUNNING
                    BackupService.R_OK -> {
                        message = resultData?.getString(BackupService.EXTRA_MSG)?.takeIf { it.isNotBlank() }
                        phase = BackupPhase.SUCCESS
                    }
                    BackupService.R_INVALID -> {
                        message = app.getString(R.string.backup_invalid)
                        phase = BackupPhase.FAILURE
                    }
                    BackupService.R_ERROR -> fail()
                }
            }
        }
        try { launch(receiver) } catch (_: Exception) { if (token == generation) fail() }
    }
}

@Composable
internal fun BackupProgressDialog(state: BackupProgressState) {
    val phase = state.phase ?: return
    val busy = phase == BackupPhase.PREPARING || phase == BackupPhase.RUNNING
    val title = when (phase) {
        BackupPhase.PREPARING -> R.string.backup_progress_preparing
        BackupPhase.RUNNING -> R.string.backup_progress_running
        BackupPhase.SUCCESS -> R.string.backup_progress_success
        BackupPhase.FAILURE -> R.string.backup_progress_failure
    }
    AlertDialog(
        onDismissRequest = {}, // Results remain until explicitly acknowledged.
        icon = { PhaseBadge(phase) },
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = { (fadeIn(tween(200, 60)) togetherWith fadeOut(tween(120))) },
                label = "bkTitle"
            ) { Text(stringResource(it), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).animateContentSize(tween(220, easing = Motion.Emphasized)),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (busy) LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    state.message ?: stringResource(when (phase) {
                        BackupPhase.PREPARING -> R.string.backup_progress_preparing_detail
                        BackupPhase.RUNNING -> R.string.backup_progress_running_detail
                        BackupPhase.SUCCESS -> R.string.backup_progress_success_detail
                        BackupPhase.FAILURE -> R.string.backup_progress_failure_detail
                    }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (!busy) FilledTonalButton(onClick = state::dismiss) { Text(stringResource(R.string.got_it)) }
        }
    )
}

/** 阶段徽标：忙碌时环形转圈，终态以 spring 弹出对勾 / 感叹号（色块随语义色过渡）。 */
@Composable
private fun PhaseBadge(phase: BackupPhase) {
    val ok = phase == BackupPhase.SUCCESS
    val fail = phase == BackupPhase.FAILURE
    val bg by animateColorAsState(
        when {
            ok -> MaterialTheme.colorScheme.primaryContainer
            fail -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }, tween(260), label = "bkBg"
    )
    Box(Modifier.size(56.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = if (ok) 1 else if (fail) 2 else 0,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(Motion.bouncy(), initialScale = 0.4f)) togetherWith fadeOut(tween(100))
            },
            label = "bkIcon"
        ) { k ->
            when (k) {
                0 -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                1 -> Icon(painterResource(R.drawable.ic_status_ok), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                else -> Icon(painterResource(R.drawable.ic_status_fail), null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
            }
        }
    }
}
