package com.noteVE.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import me.tatarka.google.material.dynamiccolor.DynamicColor
import me.tatarka.google.material.dynamiccolor.DynamicScheme
import me.tatarka.google.material.dynamiccolor.MaterialDynamicColors
import me.tatarka.google.material.hct.Hct
import me.tatarka.google.material.quantize.QuantizerCelebi
import me.tatarka.google.material.scheme.SchemeTonalSpot
import me.tatarka.google.material.score.Score
import java.util.concurrent.atomic.AtomicReference

/**
 * 动态取色兼容层 —— **让 Android 12 以下也能用上 M3 动态取色**。
 *
 * ## 为什么需要它
 *
 * 系统原生的 `dynamicLightColorScheme / dynamicDarkColorScheme` 仅 **Android 12（API 31）**
 * 及以上可用。本项目 `minSdk = 24`，若不额外处理，绝大多数设备（含本项目真机
 * Android 9）都享受不到动态取色。
 *
 * ## 做法
 *
 * 低版本不需要另造算法 —— 直接在应用内本地运行 **与系统同一套 CAM16/HCT**：
 *
 * ```
 * 系统壁纸 → 量化取主色（QuantizerCelebi + Score）
 *          → HCT 种子
 *          → SchemeTonalSpot（M3 标准 Tonal Spot 方案）
 *          → 各 M3 令牌 ARGB → Compose ColorScheme
 * ```
 *
 * 使用的库为 Google 官方 `material-color-utilities` 的 Java 移植
 * （`me.tatarka.google.material:material-color-utilities`，Apache-2.0），
 * 唯一的传递依赖是 `androidx.annotation`。
 *
 * 这样「系统动态（API 31+）」与「本地生成（API < 31）」得到的是**同一套**配色规则，
 * 亮/深、动态/静态在各 Android 版本上表现一致。
 *
 * ## 性能开销
 *
 * 取色只在**应用启动后触发一次**，且做了三层控制：
 *
 * | 措施 | 说明 |
 * |---|---|
 * | **后台线程** | 全程在 `Dispatchers.IO` 执行，不阻塞 UI |
 * | **内存缓存** | 结果按亮/深各缓存一份（进程内），切主题不再重算 |
 * | **降采样** | 壁纸先缩到 [SAMPLE_SIZE]² 再量化（原图可能 1080×2400） |
 *
 * 单次实际开销：解码壁纸（一次性）＋ 量化 16k 像素 + 生成方案，通常在几十毫秒量级。
 * 失败（无权限 / 无壁纸 / 库异常）时静默回退静态配色，不影响启动。
 */
object DynamicColorsCompat {

    private const val TAG = "NoteAppDynamicColor"

    /** 量化前的降采样边长（px）。128² ≈ 16k 像素，足够稳定取色，开销可忽略。 */
    private const val SAMPLE_SIZE = 128

    /** 量化调色板容量（M3 官方推荐值）。 */
    private const val QUANTIZE_COLORS = 128

    /**
     * 用户关闭动态取色（或取色失败）时使用的固定种子。
     *
     * 取 `#315DA8`（HCT 266/48/40）—— 与静态色板的种子**完全一致**，
     * 保证「动态取色失败时」与「关闭动态取色时」观感连贯。
     */
    val FALLBACK_SEED: Int = 0xFF315DA8.toInt()

    private val mdc = MaterialDynamicColors()

    /** 进程内缓存：种子色只解析一次；亮/深方案各缓一份。 */
    private val cachedSeed = AtomicReference<Int?>(null)
    private val cachedLight = AtomicReference<ColorScheme?>(null)
    private val cachedDark = AtomicReference<ColorScheme?>(null)

    /**
     * 取得系统动态方案（API 31+）。
     *
     * 说明：部分国产 ROM 阉割了 Monet 或返回默认色，此时由调用方决定是否回退。
     */
    fun systemScheme(context: Context, dark: Boolean): ColorScheme =
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    /**
     * 本地生成方案（API < 31，或系统动态不可用时）。
     *
     * ★ 必须在后台线程调用（会解码壁纸）。
     *
     * @return 生成的方案；失败返回 null（调用方应回退静态配色）
     */
    fun localScheme(context: Context, dark: Boolean): ColorScheme? {
        // 命中缓存直接返回
        (if (dark) cachedDark else cachedLight).get()?.let { return it }

        return try {
            val extracted = cachedSeed.get() ?: extractSeed(context)?.also { cachedSeed.set(it) }
            val seed = extracted ?: FALLBACK_SEED
            val scheme = buildScheme(seed, dark)
            (if (dark) cachedDark else cachedLight).set(scheme)

            // 成功也打日志：便于确认「取色确实跑了」以及取到的是什么种子
            val hct = Hct.fromInt(seed)
            Log.i(
                TAG,
                "本地动态取色完成 dark=$dark " +
                    "seed=#${"%06X".format(seed and 0xFFFFFF)} " +
                    "HCT=${Math.round(hct.hue)}/${Math.round(hct.chroma)}/${Math.round(hct.tone)} " +
                    (if (extracted == null) "(壁纸不可用，用默认种子)" else "(来自壁纸)")
            )
            scheme
        } catch (t: Throwable) {
            Log.w(TAG, "本地动态取色失败，回退静态配色", t)
            null
        }
    }

    /** 由种子色 + 明暗构建 M3 方案（不依赖壁纸，可在无壁纸时自行指定种子）。 */
    fun buildScheme(seedArgb: Int, dark: Boolean): ColorScheme {
        val ds = SchemeTonalSpot(Hct.fromInt(seedArgb), dark, 0.0)
        return ds.toColorScheme()
    }

    // ───────────────────────────────────────────────────────────
    // 壁纸取种子色
    // ───────────────────────────────────────────────────────────

    /**
     * 从系统壁纸提取「最具代表性」的颜色作为种子。
     *
     * 与 M3 官方做法一致：量化 → 打分排序 → 取第一名。
     *
     * @return ARGB；不可用时返回 null
     */
    private fun extractSeed(context: Context): Int? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable ?: return null

            // 降采样后再取样（原图可能 1080×2400，直接逐像素会显著浪费）
            val small = drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, null)
            try {
                val w = small.width
                val h = small.height
                if (w <= 0 || h <= 0) return null

                val pixels = IntArray(w * h)
                small.getPixels(pixels, 0, w, 0, 0, w, h)

                val quantized = QuantizerCelebi.quantize(pixels, QUANTIZE_COLORS)
                Score.score(quantized).firstOrNull()
            } finally {
                // toBitmap 尺寸不同时会新建位图；若是同一对象则不回收（避免影响源 drawable）
                if (small !== (drawable as? BitmapDrawable)?.bitmap) small.recycle()
            }
        } catch (t: Throwable) {
            // 常见原因：无壁纸读取权限、壁纸缺失、OEM 限制
            Log.w(TAG, "读取壁纸种子色失败", t)
            null
        }
    }

    // ───────────────────────────────────────────────────────────
    // DynamicScheme → Compose ColorScheme
    // ───────────────────────────────────────────────────────────

    /**
     * 把库的 [DynamicScheme] 映射到 Compose 的 [ColorScheme]。
     *
     * 除 M3 标准令牌外，还映射了 `surfaceContainer*` 系列
     * （卡片/分层背景用，Compose 1.2+ 的新令牌）。
     * `surfaceTint` 与 `scrim` 按 M3 规范取 primary 与固定黑。
     */
    private fun DynamicScheme.toColorScheme(): ColorScheme {
        fun c(dc: DynamicColor) = Color(dc.getArgb(this))

        val primaryC = c(mdc.primary())
        return if (isDark) {
            darkColorScheme(
                primary = primaryC,
                onPrimary = c(mdc.onPrimary()),
                primaryContainer = c(mdc.primaryContainer()),
                onPrimaryContainer = c(mdc.onPrimaryContainer()),
                inversePrimary = c(mdc.inversePrimary()),

                secondary = c(mdc.secondary()),
                onSecondary = c(mdc.onSecondary()),
                secondaryContainer = c(mdc.secondaryContainer()),
                onSecondaryContainer = c(mdc.onSecondaryContainer()),

                tertiary = c(mdc.tertiary()),
                onTertiary = c(mdc.onTertiary()),
                tertiaryContainer = c(mdc.tertiaryContainer()),
                onTertiaryContainer = c(mdc.onTertiaryContainer()),

                background = c(mdc.background()),
                onBackground = c(mdc.onBackground()),
                surface = c(mdc.surface()),
                onSurface = c(mdc.onSurface()),
                surfaceVariant = c(mdc.surfaceVariant()),
                onSurfaceVariant = c(mdc.onSurfaceVariant()),
                surfaceTint = primaryC,

                surfaceBright = c(mdc.surfaceBright()),
                surfaceDim = c(mdc.surfaceDim()),
                surfaceContainerLowest = c(mdc.surfaceContainerLowest()),
                surfaceContainerLow = c(mdc.surfaceContainerLow()),
                surfaceContainer = c(mdc.surfaceContainer()),
                surfaceContainerHigh = c(mdc.surfaceContainerHigh()),
                surfaceContainerHighest = c(mdc.surfaceContainerHighest()),

                inverseSurface = c(mdc.inverseSurface()),
                inverseOnSurface = c(mdc.inverseOnSurface()),

                error = c(mdc.error()),
                onError = c(mdc.onError()),
                errorContainer = c(mdc.errorContainer()),
                onErrorContainer = c(mdc.onErrorContainer()),

                outline = c(mdc.outline()),
                outlineVariant = c(mdc.outlineVariant()),
                scrim = c(mdc.scrim()),
            )
        } else {
            lightColorScheme(
                primary = primaryC,
                onPrimary = c(mdc.onPrimary()),
                primaryContainer = c(mdc.primaryContainer()),
                onPrimaryContainer = c(mdc.onPrimaryContainer()),
                inversePrimary = c(mdc.inversePrimary()),

                secondary = c(mdc.secondary()),
                onSecondary = c(mdc.onSecondary()),
                secondaryContainer = c(mdc.secondaryContainer()),
                onSecondaryContainer = c(mdc.onSecondaryContainer()),

                tertiary = c(mdc.tertiary()),
                onTertiary = c(mdc.onTertiary()),
                tertiaryContainer = c(mdc.tertiaryContainer()),
                onTertiaryContainer = c(mdc.onTertiaryContainer()),

                background = c(mdc.background()),
                onBackground = c(mdc.onBackground()),
                surface = c(mdc.surface()),
                onSurface = c(mdc.onSurface()),
                surfaceVariant = c(mdc.surfaceVariant()),
                onSurfaceVariant = c(mdc.onSurfaceVariant()),
                surfaceTint = primaryC,

                surfaceBright = c(mdc.surfaceBright()),
                surfaceDim = c(mdc.surfaceDim()),
                surfaceContainerLowest = c(mdc.surfaceContainerLowest()),
                surfaceContainerLow = c(mdc.surfaceContainerLow()),
                surfaceContainer = c(mdc.surfaceContainer()),
                surfaceContainerHigh = c(mdc.surfaceContainerHigh()),
                surfaceContainerHighest = c(mdc.surfaceContainerHighest()),

                inverseSurface = c(mdc.inverseSurface()),
                inverseOnSurface = c(mdc.inverseOnSurface()),

                error = c(mdc.error()),
                onError = c(mdc.onError()),
                errorContainer = c(mdc.errorContainer()),
                onErrorContainer = c(mdc.onErrorContainer()),

                outline = c(mdc.outline()),
                outlineVariant = c(mdc.outlineVariant()),
                scrim = c(mdc.scrim()),
            )
        }
    }
}
