package com.wkcw.scanify.ui.theme

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

// A single, calm green palette shared by every screen (matches the launcher icon).
private val LightColors = lightColorScheme(
    primary = Color(0xFF5F7A3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EED6),
    onPrimaryContainer = Color(0xFF1B2A0B),

    secondary = Color(0xFF6A7A5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EEE2),
    onSecondaryContainer = Color(0xFF202A17),

    tertiary = Color(0xFF7D8A72),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDF1E7),
    onTertiaryContainer = Color(0xFF262F1F),

    surface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F5),
    surfaceContainer = Color(0xFFF2F4EE),
    surfaceContainerHigh = Color(0xFFE9EDE3),
    surfaceContainerHighest = Color(0xFFE2E7DB),
    onSurface = Color(0xFF1A1C17),
    onSurfaceVariant = Color(0xFF5D6355),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4D087),
    onPrimary = Color(0xFF1F2C0E),
    primaryContainer = Color(0xFF3E5223),
    onPrimaryContainer = Color(0xFFDCEBC2),

    secondary = Color(0xFFB8C4AC),
    onSecondary = Color(0xFF222A1B),
    secondaryContainer = Color(0xFF35402E),
    onSecondaryContainer = Color(0xFFDCE4D5),

    tertiary = Color(0xFFC3CCB9),
    onTertiary = Color(0xFF262F20),
    tertiaryContainer = Color(0xFF3B4538),
    onTertiaryContainer = Color(0xFFE2E9DE),

    surface = Color(0xFF12150F),
    surfaceContainerLowest = Color(0xFF0C0E0A),
    surfaceContainerLow = Color(0xFF1A1E16),
    surfaceContainer = Color(0xFF21261D),
    surfaceContainerHigh = Color(0xFF2B3125),
    surfaceContainerHighest = Color(0xFF363D2F),
    onSurface = Color(0xFFE4E8DE),
    onSurfaceVariant = Color(0xFFB4BDAA),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 52.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun ScanifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
