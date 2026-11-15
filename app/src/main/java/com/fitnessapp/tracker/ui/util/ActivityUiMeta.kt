package com.fitnessapp.tracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitnessapp.tracker.data.local.entity.ActivityType

/**
 * UI-layer display extensions for [ActivityType] so entities stay decoupled from Compose icons.
 */
val ActivityType.icon: ImageVector
    get() = when (this) {
        ActivityType.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
        ActivityType.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
        ActivityType.JOGGING -> Icons.AutoMirrored.Filled.DirectionsRun
    }

val ActivityType.statsCountTitle: String
    get() = "Total ${nounPlural.replaceFirstChar { it.uppercase() }}"

