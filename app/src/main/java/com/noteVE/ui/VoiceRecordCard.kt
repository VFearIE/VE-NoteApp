package com.noteVE.ui

import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noteVE.R
import com.noteVE.domain.VoicePlayer
import com.noteVE.domain.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 录音卡片：边框 + 名字 + 播放/暂停 + 灰色进度条实时填涂 + 时间，长按（短震动+遮罩）弹操作。 */
@Composable
fun VoiceRecordCard(
    name: String,
    file: File,
    player: VoicePlayer,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val isThis = player.playingName == name
    val playing = player.isPlaying(name)
    val positionMs = if (isThis) player.positionMs else 0
    val liveDuration = if (isThis && player.durationMs > 0) player.durationMs else rememberDuration(file)
    val fraction = if (liveDuration > 0) positionMs.toFloat() / liveDuration else 0f

    var pressed by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(if (pressed) 0.35f else 0f, label = "scrim")

    val border = MaterialTheme.colorScheme.outline
    val bar = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(name) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = {
                        vibrate(context)
                        onLongPress()
                    }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.toggle(file, name) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_voice_pause else R.drawable.ic_voice_play),
                        stringResource(if (playing) R.string.pause else R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    name.removeSuffix(".m4a"),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "${formatDuration(positionMs)}/${formatDuration(liveDuration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bar)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(fill)
                )
            }
        }
        if (scrimAlpha > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrimAlpha)))
        }
    }
}

@Composable
private fun rememberDuration(file: File): Int {
    return produceState(initialValue = 0, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                mmr.release()
                d
            }.getOrDefault(0)
        }
    }.value
}
