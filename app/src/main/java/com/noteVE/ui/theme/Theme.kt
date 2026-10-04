package com.noteVE.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VE笔记 设计系统。
 *
 * 配色：Shizuku 实测（app_color_light #3F51B5 / app_color_dark #B1B8DF /
 *       background_light #FAFAFA / background_dark #212121 / primary_dark_dark #101010）
 * 形状与尺寸：Thanox 实测（圆角 24dp、边距 16dp 起、列表项 64dp、工具栏 0dp 扁平）
 * 动画与开关控件：参考 Vector 管理器（tween 驱动、M3 Switch/AnimatedVisibility/Crossfade）
 */

// ---------- 强调色：Shizuku 靛蓝（实测 app_color_light / app_color_dark） ----------
private val Indigo10 = Color(0xFF0A0F33)
private val Indigo20 = Color(0xFF1A237E)
private val Indigo30 = Color(0xFF283593)
private val Indigo40 = Color(0xFF3F51B5)   // Shizuku app_color_light（Material Indigo 500）
private val Indigo80 = Color(0xFFB1B8DF)   // Shizuku app_color_dark
private val Indigo90 = Color(0xFFDDE1FF)

// ---------- 中性阶（Shizuku 背景 + Thanox 深色层级） ----------
private val N0 = Color(0xFF000000)
private val N10 = Color(0xFF101010)        // Shizuku primary_dark_dark
private val N16 = Color(0xFF1A1A1A)
private val N22 = Color(0xFF212121)        // Shizuku background_dark
private val N28 = Color(0xFF272727)
private val N44 = Color(0xFF444444)
private val N88 = Color(0xFF888888)
private val NCC = Color(0xFFCCCCCC)
private val NFA = Color(0xFFFAFAFA)        // Shizuku background_light（material_grey_50）
private val NF1 = Color(0xFFF1F1F4)

private val Slate40 = Color(0xFF5A5D72)
private val Rose40 = Color(0xFFB3261E)
private val Rose80 = Color(0xFFF2B8B5)

val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,

    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E6EF),
    onSecondaryContainer = Color(0xFF17182B),

    tertiary = Color(0xFF3F6B5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2ECD9),
    onTertiaryContainer = Color(0xFF002115),

    error = Rose40,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    background = NFA,                       // Shizuku background_light
    onBackground = Color(0xFF1A1B20),

    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE3E3E8),
    onSurfaceVariant = Color(0xFF45464F),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = NF1,              // 卡片底
    surfaceContainer = Color(0xFFEBEBEF),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFDEDEE3),

    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFCBCAD6),

    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Indigo80,

    scrim = N0
)

val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,

    secondary = NCC,
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = N44,
    onSecondaryContainer = Color(0xFFE4E6EF),

    tertiary = Color(0xFFA6D0BF),
    onTertiary = Color(0xFF0B3729),
    tertiaryContainer = Color(0xFF26513F),
    onTertiaryContainer = Color(0xFFC2ECD9),

    error = Rose80,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = N22,                       // Shizuku background_dark
    onBackground = Color(0xFFE6E4EC),

    surface = N28,                          // 比背景亮一档，形成层次
    onSurface = Color(0xFFE6E4EC),
    surfaceVariant = N44,
    onSurfaceVariant = NCC,

    surfaceContainerLowest = N16,
    surfaceContainerLow = Color(0xFF2C2C2C),// 卡片底
    surfaceContainer = Color(0xFF323232),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = Color(0xFF434343),

    outline = N88,
    outlineVariant = N44,

    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Indigo40,

    scrim = N0
)

/**
 * 形状：取 Thanox 实测值 —— 卡片圆角 24dp（common_view_corner_radius），
 * 控件圆角 16dp，小控件 12dp。Vector 的胶囊取 extraLarge。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),   // Thanox common_view_corner_radius
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

@Composable
fun NoteAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
