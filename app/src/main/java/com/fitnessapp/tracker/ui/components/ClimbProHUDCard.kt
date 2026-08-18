package com.fitnessapp.tracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.navigation.LiveClimbInfo
import com.fitnessapp.tracker.theme.*

@Composable
fun ClimbProHUDCard(
    liveClimb: LiveClimbInfo,
    modifier: Modifier = Modifier
) {
    val climb = liveClimb.climb
    val isAscending = liveClimb.isAscending

    val categoryColor = when (climb.category) {
        "HC" -> SpeedRed
        "Cat 1" -> Color(0xFFFF5722)
        "Cat 2" -> WarningAmber
        "Cat 3" -> VividCyan
        else -> ElectricGreen
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (liveClimb.progressPct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "climbProgress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, categoryColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terrain,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isAscending) "⛰️ CLIMB #${climb.id} (${climb.category})" else "🔜 UPCOMING CLIMB #${climb.id}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = categoryColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isAscending) {
                                "${"%.1f".format(climb.avgGradientPct)}% Avg • Max ${"%.1f".format(climb.maxGradientPct)}%"
                            } else {
                                "Starts in ${"%.0f".format(liveClimb.distanceToStartMeters)}m • ${"%.1f".format(climb.avgGradientPct)}% Avg"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Climb Category Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = climb.category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = categoryColor
                    )
                }
            }

            // Climb Progress Bar
            if (isAscending) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(DeepNavy)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(categoryColor.copy(alpha = 0.6f), categoryColor)
                                )
                            )
                    )
                }
            }

            // Real-time Summit Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAscending) {
                        "Remaining: ${"%.2f".format(liveClimb.distanceToSummitMeters / 1000.0)} km"
                    } else {
                        "Length: ${"%.2f".format(climb.lengthMeters / 1000.0)} km"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = if (isAscending) {
                        "+${"%.0f".format(liveClimb.elevationRemainingMeters)}m to crest"
                    } else {
                        "+${"%.0f".format(climb.elevationGainMeters)}m gain"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
