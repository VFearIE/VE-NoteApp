package com.noteVE.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VE笔记 设计系统。
 *
 * ## 配色：标准 M3 / Material You（种子 `#315DA8`，HCT 266/48/40）
 *
 * 下方所有色值均由 Google 官方算法（`SchemeTonalSpot`）从该种子**正向生成**，
 * 而非手工挑选 —— 这样「静态配色」「系统动态取色」「低版本本地取色」
 * 三条路径遵循**同一套规则**，各 Android 版本表现一致。
 *
 * ### 种子为何选 hue 266
 *
 * | hue | 观感 |
 * |---|---|
 * | 274（M3 默认紫邻域） | 偏紫 —— 在手表小屏上尤其明显 |
 * | **266（本方案）** | **蓝感明显更强，深色下依旧优雅** |
 * | 262 | 更蓝，但已开始偏青 |
 *
 * 说明：`SchemeTonalSpot` 会将主色阶彩度规范到 36（与种子彩度无关），
 * 因此「偏紫」的感受主要来自**去饱和后的灰调**，而非单纯色相偏移；
 * 提高 hue 的蓝度是更有效的改善手段。
 *
 * ## 形状与字阶
 *
 * 形状沿用实测规格（卡片圆角 24dp 等）；字阶为手表小屏收敛过字号跨度。
 */

// ═══════════════════════════════════════════════════════════════
// 亮色（种子 #315DA8 · SchemeTonalSpot）
// ═══════════════════════════════════════════════════════════════
private val L_Primary = Color(0xFF445E91)
private val L_OnPrimary = Color(0xFFFFFFFF)
private val L_PrimaryContainer = Color(0xFFD8E2FF)
private val L_OnPrimaryContainer = Color(0xFF2B4678)
private val L_InversePrimary = Color(0xFFADC6FF)
private val L_Secondary = Color(0xFF565E71)
private val L_OnSecondary = Color(0xFFFFFFFF)
private val L_SecondaryContainer = Color(0xFFDBE2F9)
private val L_OnSecondaryContainer = Color(0xFF3F4759)
private val L_Tertiary = Color(0xFF715573)
private val L_OnTertiary = Color(0xFFFFFFFF)
private val L_TertiaryContainer = Color(0xFFFBD7FC)
private val L_OnTertiaryContainer = Color(0xFF583E5B)
private val L_Surface = Color(0xFFF9F9FF)
private val L_OnSurface = Color(0xFF1A1B20)
private val L_SurfaceVariant = Color(0xFFE1E2EC)
private val L_OnSurfaceVariant = Color(0xFF44474F)
private val L_Outline = Color(0xFF74777F)
private val L_OutlineVariant = Color(0xFFC4C6D0)
private val L_Error = Color(0xFFBA1A1A)
private val L_OnError = Color(0xFFFFFFFF)
private val L_ErrorContainer = Color(0xFFFFDAD6)
private val L_OnErrorContainer = Color(0xFF93000A)
private val L_InverseSurface = Color(0xFF2F3036)
private val L_InverseOnSurface = Color(0xFFF0F0F7)

val LightColors = lightColorScheme(
    primary = L_Primary,
    onPrimary = L_OnPrimary,
    primaryContainer = L_PrimaryContainer,
    onPrimaryContainer = L_OnPrimaryContainer,
    inversePrimary = L_InversePrimary,

    secondary = L_Secondary,
    onSecondary = L_OnSecondary,
    secondaryContainer = L_SecondaryContainer,
    onSecondaryContainer = L_OnSecondaryContainer,

    tertiary = L_Tertiary,
    onTertiary = L_OnTertiary,
    tertiaryContainer = L_TertiaryContainer,
    onTertiaryContainer = L_OnTertiaryContainer,

    background = L_Surface,
    onBackground = L_OnSurface,
    surface = L_Surface,
    onSurface = L_OnSurface,
    surfaceVariant = L_SurfaceVariant,
    onSurfaceVariant = L_OnSurfaceVariant,
    surfaceTint = L_Primary,

    surfaceBright = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),

    inverseSurface = L_InverseSurface,
    inverseOnSurface = L_InverseOnSurface,

    error = L_Error,
    onError = L_OnError,
    errorContainer = L_ErrorContainer,
    onErrorContainer = L_OnErrorContainer,

    outline = L_Outline,
    outlineVariant = L_OutlineVariant,
    scrim = Color(0xFF000000)
)

// ═══════════════════════════════════════════════════════════════
// 深色（同种子）
// ═══════════════════════════════════════════════════════════════
private val D_Primary = Color(0xFFADC6FF)
private val D_OnPrimary = Color(0xFF102F60)
private val D_PrimaryContainer = Color(0xFF2B4678)
private val D_OnPrimaryContainer = Color(0xFFD8E2FF)
private val D_InversePrimary = Color(0xFF445E91)
private val D_Secondary = Color(0xFFBFC6DC)
private val D_OnSecondary = Color(0xFF293041)
private val D_SecondaryContainer = Color(0xFF3F4759)
private val D_OnSecondaryContainer = Color(0xFFDBE2F9)
private val D_Tertiary = Color(0xFFDEBCDF)
private val D_OnTertiary = Color(0xFF402843)
private val D_TertiaryContainer = Color(0xFF583E5B)
private val D_OnTertiaryContainer = Color(0xFFFBD7FC)
private val D_Surface = Color(0xFF111318)
private val D_OnSurface = Color(0xFFE2E2E9)
private val D_SurfaceVariant = Color(0xFF44474F)
private val D_OnSurfaceVariant = Color(0xFFC4C6D0)
private val D_Outline = Color(0xFF8E9099)
private val D_OutlineVariant = Color(0xFF44474F)
private val D_Error = Color(0xFFFFB4AB)
private val D_OnError = Color(0xFF690005)
private val D_ErrorContainer = Color(0xFF93000A)
private val D_OnErrorContainer = Color(0xFFFFDAD6)
private val D_InverseSurface = Color(0xFFE2E2E9)
private val D_InverseOnSurface = Color(0xFF2F3036)

val DarkColors = darkColorScheme(
    primary = D_Primary,
    onPrimary = D_OnPrimary,
    primaryContainer = D_PrimaryContainer,
    onPrimaryContainer = D_OnPrimaryContainer,
    inversePrimary = D_InversePrimary,

    secondary = D_Secondary,
    onSecondary = D_OnSecondary,
    secondaryContainer = D_SecondaryContainer,
    onSecondaryContainer = D_OnSecondaryContainer,

    tertiary = D_Tertiary,
    onTertiary = D_OnTertiary,
    tertiaryContainer = D_TertiaryContainer,
    onTertiaryContainer = D_OnTertiaryContainer,

    background = D_Surface,
    onBackground = D_OnSurface,
    surface = D_Surface,
    onSurface = D_OnSurface,
    surfaceVariant = D_SurfaceVariant,
    onSurfaceVariant = D_OnSurfaceVariant,
    surfaceTint = D_Primary,

    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),

    inverseSurface = D_InverseSurface,
    inverseOnSurface = D_InverseOnSurface,

    error = D_Error,
    onError = D_OnError,
    errorContainer = D_ErrorContainer,
    onErrorContainer = D_OnErrorContainer,

    outline = D_Outline,
    outlineVariant = D_OutlineVariant,
    scrim = Color(0xFF000000)
)

/**
 * 形状：沿用实测规格（卡片圆角 24dp、控件 16dp、小控件 12dp）。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 字阶：收敛字号跨度，避免手表小屏上层次混乱。 */
val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

/**
 * 应用主题。
 *
 * ## 配色来源优先级
 *
 * | 条件 | 来源 |
 * |---|---|
 * | 动态取色开 **且** API ≥ 31 | 系统 `dynamicLight/DarkColorScheme`（跟随壁纸） |
 * | 动态取色开 **且** API < 31 | **应用内本地生成**（详见 [DynamicColorsCompat]） |
 * | 动态取色关 / 取色失败 | 上方静态色板（种子 `#315DA8`） |
 *
 * 低版本路径在**后台线程**计算，未就绪时先显示静态配色，就绪后自动切换 —— 不阻塞启动。
 *
 * ## 状态栏
 *
 * 把系统状态栏颜色与图标明暗同步为主题表面色，
 * 避免「深色应用 + 浅色状态栏」的割裂观感。
 */
@Composable
fun NoteAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // 低版本动态方案（API < 31）：后台算一次，就绪前先用静态配色
    var localScheme by remember { mutableStateOf<ColorScheme?>(null) }

    LaunchedEffect(dynamicColor, darkTheme) {
        if (!dynamicColor) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@LaunchedEffect
        // 命中缓存时会立即返回，不会重复解码壁纸
        localScheme = withContext(Dispatchers.IO) {
            DynamicColorsCompat.localScheme(context, darkTheme)
        }
    }

    val colorScheme: ColorScheme = when {
        // Android 12+：直接用系统动态取色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            DynamicColorsCompat.systemScheme(context, darkTheme)

        // Android 12 以下：用本地生成的方案
        dynamicColor && localScheme != null -> localScheme!!

        // 关闭动态取色，或低版本取色尚未就绪 / 取色失败
        darkTheme -> DarkColors
        else -> LightColors
    }

    // 状态栏跟随主题表面色（含图标明暗），避免与内容区割裂
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}

/**
 * 从 Context 中找出宿主 Activity。
 *
 * Compose 里 `LocalView.current.context` 可能是 `ContextWrapper` 链上的包装类，
 * 直接 `as? Activity` 并不可靠，故沿 `baseContext` 逐层解包。
 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
