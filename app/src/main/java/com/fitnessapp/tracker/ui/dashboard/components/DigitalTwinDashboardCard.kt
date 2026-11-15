package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.engine.AcwrZone
import com.fitnessapp.tracker.engine.DigitalTwinState
import com.fitnessapp.tracker.theme.*

@Composable
fun DigitalTwinDashboardCard(
    twinState: DigitalTwinState?,
    isRefreshingWeather: Boolean,
    onRefreshWeather: () -> Unit,
    onQuickCheckInClick: () -> Unit,
    onOpenTwinHub: () -> Unit
) {
    if (twinState == null) return

    val readinessColor = when {
        twinState.readinessScore >= 75 -> ElectricGreen
        twinState.readinessScore >= 50 -> VividCyan
        twinState.readinessScore >= 35 -> WarningAmber
        else -> SpeedRed
    }
    val acwrColor = when (twinState.acwrZone) {
        AcwrZone.OPTIMAL -> ElectricGreen
        AcwrZone.DETRAINING, AcwrZone.BUILDING_BASE -> VividCyan
        AcwrZone.ELEVATED_RISK -> WarningAmber
        AcwrZone.OVERTRAINING -> SpeedRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenTwinHub),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(readinessColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = readinessColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Digital Twin Readiness",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = twinState.environment.shortSummary(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshWeather,
                    enabled = !isRefreshingWeather,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isRefreshingWeather) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ElectricGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Outdoor Environment",
                            tint = VividCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NavyMedium)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TwinStatItem("Readiness", "${twinState.readinessScore}/100", readinessColor)
                TwinStatItem("Battery", "${twinState.bodyBattery}%", VividCyan)
                TwinStatItem("ACWR", "${"%.2f".format(twinState.acwr)}", acwrColor)
                TwinStatItem("Hydration", "${"%.2f".format(twinState.hydrationRateLitersPerHour)} L/h", ElectricGreen)
            }

            Text(
                text = twinState.guidanceText,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onQuickCheckInClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VividCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = VividCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check-in", color = VividCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onOpenTwinHub,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGreen,
                        contentColor = DeepNavy
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Twin Hub", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PreWorkoutCheckInDialog(
    latestCheckIn: WellnessCheckInEntity?,
    onDismiss: () -> Unit,
    onSubmit: (sleep: Int, energy: Int, soreness: Int, stress: Int, hydration: Int) -> Unit
) {
    var sleep by remember { mutableIntStateOf(latestCheckIn?.sleepQuality ?: 4) }
    var energy by remember { mutableIntStateOf(latestCheckIn?.energyLevel ?: 4) }
    var soreness by remember { mutableIntStateOf(latestCheckIn?.muscleSoreness ?: 2) }
    var stress by remember { mutableIntStateOf(latestCheckIn?.stressLevel ?: 2) }
    var hydration by remember { mutableIntStateOf(latestCheckIn?.hydrationScore ?: 3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text("Pre-Workout Twin Check-in", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Calibrate readiness & fatigue before starting your workout (1–5).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                DialogScoreRow("Sleep", sleep) { sleep = it }
                DialogScoreRow("Energy", energy) { energy = it }
                DialogScoreRow("Soreness (1 fresh–5 sore)", soreness) { soreness = it }
                DialogScoreRow("Stress (1 calm–5 high)", stress) { stress = it }
                DialogScoreRow("Hydration", hydration) { hydration = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(sleep, energy, soreness, stress, hydration) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun TwinStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun DialogScoreRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: $value/5", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { score ->
                val selected = score == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) VividCyan.copy(alpha = 0.25f) else NavyMedium)
                        .clickable { onChange(score) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$score",
                        color = if (selected) VividCyan else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

