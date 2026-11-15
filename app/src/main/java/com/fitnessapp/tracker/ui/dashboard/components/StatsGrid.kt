package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.theme.ElectricGreen
import com.fitnessapp.tracker.theme.GlassBorder
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.SpeedRed
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
import com.fitnessapp.tracker.theme.VividCyan
import com.fitnessapp.tracker.theme.WarningAmber
import com.fitnessapp.tracker.ui.dashboard.DashboardUiState
import com.fitnessapp.tracker.ui.util.icon
import com.fitnessapp.tracker.ui.util.statsCountTitle
@Composable
fun StatsRow(uiState: DashboardUiState, activityType: String) {
    val activity = ActivityType.fromString(activityType)
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
            icon = Icons.AutoMirrored.Filled.ShowChart,
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
            label = activity.statsCountTitle,
            value = "${uiState.totalSessions}",
            unit = activity.nounPlural,
            icon = activity.icon,
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

