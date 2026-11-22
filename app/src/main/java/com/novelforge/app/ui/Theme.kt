package com.novelforge.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 素白 + 墨 + 冷灰蓝 ────────────────────────────────────────
// 底色近白不偏黄，强调色只用一个冷蓝，克制
private val Ink = Color(0xFF14161A)
private val InkSoft = Color(0xFF5A6068)
private val Paper = Color(0xFFFAFAF8)
private val CardWhite = Color(0xFFFFFFFF)
private val Steel = Color(0xFF3A6EA5)
private val SteelSoft = Color(0xFFE4EDF7)
private val Slate = Color(0xFF6B7280)

// 深色：石墨底 + 冷蓝提亮
private val NightBg = Color(0xFF0E1013)
private val NightSurface = Color(0xFF17191D)
private val NightSurface2 = Color(0xFF212429)
private val NightInk = Color(0xFFE7E8EA)
private val NightSteel = Color(0xFF7FA8D9)

// 圆角：大而柔和
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

// 字阶：整体压小一号，标题不再顶到屏幕边
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
)

private val LightColors = lightColorScheme(
    primary = Steel,
    onPrimary = Color.White,
    primaryContainer = SteelSoft,
    onPrimaryContainer = Color(0xFF102A44),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEEF0),
    onSecondaryContainer = Color(0xFF24272B),
    tertiary = Color(0xFF4F7A6E),
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFF0F2),
    onSurfaceVariant = InkSoft,
    surfaceContainer = Color(0xFFF4F5F6),
    surfaceContainerHigh = Color(0xFFEDEEF0),
    surfaceContainerLow = Color(0xFFFCFCFB),
    outline = Color(0xFFB6BAC1),
    outlineVariant = Color(0xFFE4E6E9),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = NightSteel,
    onPrimary = Color(0xFF0A2036),
    primaryContainer = Color(0xFF1E3350),
    onPrimaryContainer = Color(0xFFD6E6F7),
    secondary = Color(0xFFB4BAC2),
    onSecondary = Color(0xFF24272B),
    secondaryContainer = Color(0xFF2A2E34),
    onSecondaryContainer = Color(0xFFE7E8EA),
    tertiary = Color(0xFF8FB8AC),
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurface2,
    onSurfaceVariant = Color(0xFFA6ACB4),
    surfaceContainer = Color(0xFF1C1F23),
    surfaceContainerHigh = Color(0xFF24272C),
    surfaceContainerLow = Color(0xFF131518),
    outline = Color(0xFF6B7178),
    outlineVariant = Color(0xFF2E3238),
    error = Color(0xFFE88B82)
)

@Composable
fun NovelForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
