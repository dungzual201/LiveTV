package com.livetv.vn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LiveColors {
    val Background = Color(0xFF060708)
    val Panel = Color(0xF0121519)
    val PanelSolid = Color(0xFF121519)
    val Elevated = Color(0xFF1A1E24)
    val Outline = Color(0xFF2C323A)
    val Text = Color(0xFFF4F6F8)
    val TextMuted = Color(0xFFA2AAB4)
    val TextDim = Color(0xFF6E7782)
    val Live = Color(0xFFFF5A5F)
    val Focus = Color(0xFFFFD166)
    val Good = Color(0xFF66D9A5)
    val Scrim = Color(0xB8000000)
}

private val tvColors = darkColorScheme(
    primary = LiveColors.Live,
    onPrimary = Color.White,
    secondary = LiveColors.Focus,
    onSecondary = Color(0xFF1B1408),
    background = LiveColors.Background,
    onBackground = LiveColors.Text,
    surface = LiveColors.PanelSolid,
    onSurface = LiveColors.Text,
    surfaceVariant = LiveColors.Elevated,
    onSurfaceVariant = LiveColors.TextMuted,
    outline = LiveColors.Outline,
    error = LiveColors.Live,
)

private val tvTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun LiveTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = tvColors,
        typography = tvTypography,
        content = content,
    )
}
