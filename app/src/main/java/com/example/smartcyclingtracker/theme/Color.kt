package com.example.smartcyclingtracker.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

val LocalActivityTheme = compositionLocalOf { "CYCLING" }

val ActualElectricGreen = Color(0xFF00FF87)
val ActualElectricGreenDark = Color(0xFF00C968)
val ActualElectricGreenDarker = Color(0xFF00964C)

val ActualAzureBlue = Color(0xFF007AFF)
val ActualAzureBlueDark = Color(0xFF005BB5)
val ActualAzureBlueDarker = Color(0xFF004080)

val ActualFieryRed = Color(0xFFFF3B30)
val ActualFieryRedDark = Color(0xFFCC2F26)
val ActualFieryRedDarker = Color(0xFF99231D)

val ElectricGreen: Color
    @Composable get() {
        val target = when (LocalActivityTheme.current) {
            "WALKING" -> ActualAzureBlue
            "JOGGING" -> ActualFieryRed
            else -> ActualElectricGreen
        }
        return animateColorAsState(target, animationSpec = tween(500), label = "Primary").value
    }

val ElectricGreenDark: Color
    @Composable get() {
        val target = when (LocalActivityTheme.current) {
            "WALKING" -> ActualAzureBlueDark
            "JOGGING" -> ActualFieryRedDark
            else -> ActualElectricGreenDark
        }
        return animateColorAsState(target, animationSpec = tween(500), label = "PrimaryDark").value
    }

val ElectricGreenDarker: Color
    @Composable get() {
        val target = when (LocalActivityTheme.current) {
            "WALKING" -> ActualAzureBlueDarker
            "JOGGING" -> ActualFieryRedDarker
            else -> ActualElectricGreenDarker
        }
        return animateColorAsState(target, animationSpec = tween(500), label = "PrimaryDarker").value
    }

// ── Surface / Background: Deep Navy ─────────────────────────────────────────
val ActualDeepNavy = Color(0xFF0A0E1A)
val ActualNavyDarker = Color(0xFF070A14)
val ActualNavyMedium = Color(0xFF111828)
val ActualNavyLight = Color(0xFF1A2235)
val ActualNavyCard = Color(0xFF1E2A3A)

val DeepNavy: Color @Composable get() = MaterialTheme.colorScheme.background
val NavyDarker: Color @Composable get() = MaterialTheme.colorScheme.background
val NavyMedium: Color @Composable get() = MaterialTheme.colorScheme.surface
val NavyLight: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val NavyCard: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant

// ── Accent: Vivid Cyan ───────────────────────────────────────────────────────
val VividCyan = Color(0xFF00D4FF)
val VividCyanDim = Color(0xFF0096B4)

// ── Alert & Speed colors ─────────────────────────────────────────────────────
val SpeedRed = Color(0xFFFF4444)
val WarningAmber = Color(0xFFFFA726)
val SuccessGreen = Color(0xFF4CAF50)

// ── Text ─────────────────────────────────────────────────────────────────────
val ActualTextPrimary = Color(0xFFF0F4FF)
val ActualTextSecondary = Color(0xFF8FA8C8)
val ActualTextDisabled = Color(0xFF4A5A6A)

val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextDisabled: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

// ── Glassmorphism ────────────────────────────────────────────────────────────
val ActualGlassWhite = Color(0x1AFFFFFF)
val ActualGlassBorder = Color(0x33FFFFFF)

val GlassWhite: Color @Composable get() = if (MaterialTheme.colorScheme.background == LightBackground) Color(0x1A000000) else ActualGlassWhite
val GlassBorder: Color @Composable get() = MaterialTheme.colorScheme.outline

// Light theme (not primary focus but included for Material3 compliance)
val LightPrimary = Color(0xFF006837)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFE5E7EB)
val LightSurface = Color(0xFFF3F4F6)
