package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.ElectricGreen
import com.fitnessapp.tracker.theme.GlassBorder
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.NavyLight
import com.fitnessapp.tracker.theme.SpeedRed
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
import com.fitnessapp.tracker.ui.util.icon
import com.fitnessapp.tracker.util.DateFormats

@Composable
fun SessionCard(
    session: WorkoutSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(session.startTime) { DateFormats.formatSessionCard(session.startTime) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NavyLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ActivityType.fromString(session.activityType).icon,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PhysicsEngine.formatDistance(session.totalDistanceMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$date  •  ${PhysicsEngine.formatDuration(session.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat(label = "Avg", value = "${"%.1f".format(session.avgSpeedKmh)} km/h")
                    MiniStat(label = "Cal", value = "${"%.0f".format(session.caloriesBurned)} kcal")
                    MiniStat(label = "Elev", value = "${"%.0f".format(session.elevationGainMeters)}m")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SpeedRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = ElectricGreen)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
