package com.noteVE.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noteVE.R
import com.noteVE.domain.ImageDecoder
import com.noteVE.domain.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片块：满宽、等比显示，长按（短震动 + 灰色遮罩动画）弹出操作。
 *
 * 显示用位图按**屏幕宽度 × 2** 降采样解码 —— 视觉上无损，内存占用降到 1/16 以下，
 * 避免多图页卡顿/OOM（详见 [ImageDecoder]）。
 */
@Composable
fun ImageBlock(file: File, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val screenWidthPx = with(LocalConfiguration.current) { screenWidthDp * densityDpi / 160 }
    val bmp = rememberBlockBitmap(file, screenWidthPx)
    var pressed by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(if (pressed) 0.35f else 0f, label = "scrim")

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(file) {
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
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
        if (scrimAlpha > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrimAlpha)))
        }
    }
}

/** 按屏宽 ×2 降采样解码。 */
@Composable
private fun rememberBlockBitmap(file: File, targetWidthPx: Int): Bitmap? {
    return produceState<Bitmap?>(initialValue = null, file, targetWidthPx) {
        value = withContext(Dispatchers.IO) {
            ImageDecoder.decode(file, targetWidthPx)
        }
    }.value
}

/** 安全震动：无权限时按设置提示，不闪退。 */
fun vibrate(context: Context) {
    if (context.checkSelfPermission(Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
        if (Settings.warnPermissionMissing.value) {
            Toast.makeText(context, R.string.permission_missing, Toast.LENGTH_SHORT).show()
        }
        return
    }
    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(50)
        }
    }
}
