package com.noteVE.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 图片解码工具。
 *
 * ## 为什么要"按屏幕尺寸定向降采样"
 *
 * 直接 `BitmapFactory.decodeFile()` 会按原图分辨率全量解码：
 * 一张 4000×3000 的照片占用约 **48MB** 内存（ARGB_8888），
 * 编辑页插入多张就必然 OOM / 严重卡顿。
 *
 * 而显示宽度最多只有屏幕宽度（本设备 480px），
 * 按 **[显示宽度 × 2]** 解码即可满足「看不出模糊」——
 * 2 倍是给高 DPI 屏留的余量（像素密度高于 dp，1:1 解码会偏软）。
 *
 * 解码时用 `inJustDecodeBounds` 先读尺寸（不分配内存），
 * 算出 2 的幂次采样率，再正式解码 → 内存占用降到 1/16 甚至更低。
 */
object ImageDecoder {

    /**
     * 按目标宽度解码。
     *
     * @param targetWidthPx 期望显示宽度（像素）。传屏幕宽度即可。
     * @param oversample    超采样倍数（默认 2，保证清晰度）
     */
    fun decode(file: File, targetWidthPx: Int, oversample: Int = 2): Bitmap? {
        if (!file.exists()) return null
        return try {
            // 第一步：只读尺寸，不分配像素内存
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            // 第二步：算采样率（2 的幂，取"不超过目标"的最大值）
            val want = (targetWidthPx * oversample).coerceAtLeast(1)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= want) sample *= 2

            // 第三步：正式解码
            val real = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565   // 无 alpha 需求，内存再省一半
            }
            BitmapFactory.decodeFile(file.absolutePath, real)
        } catch (t: Throwable) {
            null
        }
    }
}
