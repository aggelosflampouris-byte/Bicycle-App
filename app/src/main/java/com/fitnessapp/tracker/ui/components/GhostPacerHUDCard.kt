package com.fitnessapp.tracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.gamification.GhostPacerState
import com.fitnessapp.tracker.theme.*
import kotlin.math.abs

@Composable
fun GhostPacerHUDCard(
    ghostState: GhostPacerState,
    modifier: Modifier = Modifier
) {
    if (!ghostState.isActive) return

    val isAhead = ghostState.isAhead
    val deltaColor = if (isAhead) ElectricGreen else SpeedRed
    val deltaSign = if (isAhead) "+" else "-"
    val absDeltaSec = abs(ghostState.timeDeltaSeconds)
    val absDeltaMeters = abs(ghostState.distanceDeltaMeters)

    val deltaFormatted = if (absDeltaSec >= 60) {
        val mins = (absDeltaSec / 60).toInt()
        val secs = (absDeltaSec % 60).toInt()
        "$deltaSign${mins}m ${secs}s"
    } else {
        "$deltaSign${"%.1f".format(absDeltaSec)}s"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, deltaColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(deltaColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👻",
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isAhead) "AHEAD OF GHOST" else "BEHIND GHOST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = deltaColor,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${deltaSign}${"%.0f".format(absDeltaMeters)}m • Ghost: ${"%.1f".format(ghostState.ghostSpeedKmh)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Big Delta Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(deltaColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = deltaFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = deltaColor
                )
            }
        }
    }
}
