package com.example.smartcyclingtracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricGreen,
    onPrimary = DeepNavy,
    primaryContainer = ElectricGreenDarker,
    onPrimaryContainer = ElectricGreen,

    secondary = VividCyan,
    onSecondary = DeepNavy,
    secondaryContainer = NavyLight,
    onSecondaryContainer = VividCyan,

    tertiary = WarningAmber,
    onTertiary = DeepNavy,

    background = DeepNavy,
    onBackground = TextPrimary,

    surface = NavyMedium,
    onSurface = TextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondary,

    error = SpeedRed,
    onError = Color.White,

    outline = GlassBorder,
    outlineVariant = NavyLight,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun SmartCyclingTrackerTheme(
    darkTheme: Boolean = true, // Default dark for outdoor readability
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
