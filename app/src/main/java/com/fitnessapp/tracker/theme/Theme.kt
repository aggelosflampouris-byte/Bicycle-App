package com.fitnessapp.tracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fitnessapp.tracker.data.local.ThemeMode

val DarkColorScheme: ColorScheme
    @Composable get() = darkColorScheme(
    primary = ElectricGreen,
    onPrimary = ActualDeepNavy,
    primaryContainer = ElectricGreenDarker,
    onPrimaryContainer = ElectricGreen,

    secondary = VividCyan,
    onSecondary = ActualDeepNavy,
    secondaryContainer = ActualNavyLight,
    onSecondaryContainer = VividCyan,

    tertiary = WarningAmber,
    onTertiary = ActualDeepNavy,

    background = ActualDeepNavy,
    onBackground = ActualTextPrimary,

    surface = ActualNavyMedium,
    onSurface = ActualTextPrimary,
    surfaceVariant = ActualNavyCard,
    onSurfaceVariant = ActualTextSecondary,

    error = SpeedRed,
    onError = Color.White,

    outline = ActualGlassBorder,
    outlineVariant = ActualNavyLight,
)

val LightColorScheme: ColorScheme
    @Composable get() = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = Color(0xFFB7F5D0),
    onPrimaryContainer = Color(0xFF00371A),

    secondary = Color(0xFF006C7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3ECFF),
    onSecondaryContainer = Color(0xFF001F27),

    tertiary = Color(0xFFB45300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),

    background = LightBackground,
    onBackground = Color(0xFF1A1A1A),

    surface = LightSurface,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF444444),

    error = Color(0xFFBA1A1A),
    onError = Color.White,

    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
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
