package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
import com.fitnessapp.tracker.ui.util.icon
@Composable
fun DashboardHeader(
    userName: String,
    activityType: String,
    onSwitchActivity: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val activity = ActivityType.fromString(activityType)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hello, $userName 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Ready to ${activity.nounSingular}?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onSwitchActivity,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NavyCard)
            ) {
                Crossfade(targetState = activityType, label = "activityIcon") { type ->
                    Icon(
                        imageVector = ActivityType.fromString(type).icon,
                        contentDescription = "Switch Activity",
                        tint = TextSecondary
                    )
                }
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NavyCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }
    }
}

