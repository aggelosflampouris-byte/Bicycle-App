package com.fitnessapp.tracker.ui.dashboard.components
import com.fitnessapp.tracker.ui.dashboard.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.*
import java.text.SimpleDateFormat
import java.util.*
import com.fitnessapp.tracker.data.local.entity.DailyPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.fitnessapp.tracker.service.CyclingTrackingService
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.data.local.entity.ChallengeMetric
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric
import android.widget.Toast


import com.fitnessapp.tracker.ui.dashboard.components.*
@Composable
fun StatsRow(uiState: DashboardUiState, activityType: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Total Distance",
            value = "${"%.1f".format(uiState.totalDistanceKm)}",
            unit = "km",
            icon = Icons.Default.Route,
            iconTint = ElectricGreen
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Avg Distance",
            value = "${"%.1f".format(uiState.avgDistanceKm)}",
            unit = "km",
            icon = Icons.Default.ShowChart,
            iconTint = VividCyan
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = when (activityType) {
                "WALKING" -> "Total Walks"
                "JOGGING" -> "Total Jogs"
                else -> "Total Rides"
            },
            value = "${uiState.totalSessions}",
            unit = when (activityType) {
                "WALKING" -> "walks"
                "JOGGING" -> "jogs"
                else -> "rides"
            },
            icon = when (activityType) {
                "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
                else -> Icons.AutoMirrored.Filled.DirectionsBike
            },
            iconTint = WarningAmber
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Calories Burned",
            value = "${"%.0f".format(uiState.totalCalories)}",
            unit = "kcal",
            icon = Icons.Default.LocalFireDepartment,
            iconTint = SpeedRed
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

