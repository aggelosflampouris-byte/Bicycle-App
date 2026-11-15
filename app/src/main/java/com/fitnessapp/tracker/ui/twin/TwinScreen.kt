package com.fitnessapp.tracker.ui.twin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
import com.fitnessapp.tracker.engine.AcwrZone
import com.fitnessapp.tracker.engine.DigitalTwinState
import com.fitnessapp.tracker.engine.RecommendedIntensity
import com.fitnessapp.tracker.engine.SimulationOutcome
import com.fitnessapp.tracker.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TwinScreen(
    viewModel: TwinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TwinHeader(
            isRefreshing = uiState.isRefreshingWeather,
            onRefresh = { viewModel.refreshEnvironment(forceRefresh = true) }
        )

        TwinReadinessHeroCard(twinState = uiState.twinState)

        OutdoorMicroclimateCard(env = uiState.twinState.environment)

        WellnessCheckInCard(
            latestCheckIn = uiState.twinState.latestCheckIn,
            savedAtMs = uiState.checkInSavedAtMs,
            onSubmit = { sleep, energy, soreness, stress, hydration, notes ->
                viewModel.submitCheckIn(
                    sleepQuality = sleep,
                    energyLevel = energy,
                    muscleSoreness = soreness,
                    stressLevel = stress,
                    hydrationScore = hydration,
                    notes = notes
                )
            }
        )

        WorkoutSimulatorCard(
            simInput = uiState.simInput,
            outcome = uiState.simulationOutcome,
            onUpdate = { sport, duration, rpe, elevation ->
                viewModel.updateSimulation(sport, duration, rpe, elevation)
            }
        )

        if (uiState.recentSnapshots.isNotEmpty()) {
            RecentSnapshotsCard(snapshots = uiState.recentSnapshots)
        }

        Spacer(modifier = Modifier.height(92.dp))
    }
}

@Composable
private fun TwinHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Digital Twin",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Physiological readiness, weather stress & simulator",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier
                .clip(CircleShape)
                .background(NavyCard)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ElectricGreen
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Outdoor Context",
                    tint = VividCyan
                )
            }
        }
    }
}

@Composable
private fun TwinReadinessHeroCard(twinState: DigitalTwinState) {
    val readinessColor = readinessColor(twinState.readinessScore)
    val acwrColor = acwrColor(twinState.acwrZone)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TwinGaugeRing(
                    score = twinState.bodyBattery,
                    label = "Body Battery",
                    sublabel = "Energy Reserve",
                    color = VividCyan
                )
                TwinGaugeRing(
                    score = twinState.readinessScore,
                    label = "Readiness",
                    sublabel = intensityShortLabel(twinState.recommendedIntensity),
                    color = readinessColor
                )
            }

            // ACWR & Load Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NavyMedium)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricMiniColumn("ACWR", "${"%.2f".format(twinState.acwr)}", acwrColor)
                MetricMiniColumn("Acute 7d", "${"%.0f".format(twinState.acuteLoad)}", TextPrimary)
                MetricMiniColumn("Chronic 28d", "${"%.0f".format(twinState.chronicLoad)}", TextSecondary)
                MetricMiniColumn("Today Strain", "${"%.0f".format(twinState.todayStrain)}", ElectricGreen)
            }

            // Recommendation Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(readinessColor.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = readinessColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${twinState.recommendedIntensity.label} • ${twinState.acwrZone.label}",
                            style = MaterialTheme.typography.titleSmall,
                            color = readinessColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = twinState.guidanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Hydration baseline: ${"%.2f".format(twinState.hydrationRateLitersPerHour)} L/hr • Env stress x${"%.2f".format(twinState.environmentStressFactor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            if (twinState.riskFlags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    twinState.riskFlags.forEach { flag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarningAmber.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = flag,
                                style = MaterialTheme.typography.labelMedium,
                                color = WarningAmber,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwinGaugeRing(
    score: Int,
    label: String,
    sublabel: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(116.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 11.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                drawArc(
                    color = color.copy(alpha = 0.16f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val sweep = 270f * (score.coerceIn(0, 100) / 100f)
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "/ 100",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun OutdoorMicroclimateCard(env: EnvironmentSnapshot) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
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
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = WarningAmber)
                    Column {
                        Text(
                            text = "Outdoor Environment",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = env.conditionLabel + if (env.isOfflineFallback) " • Cached" else " • Live Open-Meteo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Text(
                    text = "${"%.0f".format(env.temperatureC)}°C",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ElectricGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeatherPill(Icons.Default.Thermostat, "Feels", "${"%.0f".format(env.apparentTemperatureC)}°C")
                WeatherPill(Icons.Default.Air, "Wind", "${"%.0f".format(env.windSpeedKmh)} km/h")
                WeatherPill(Icons.Default.WaterDrop, "Hum / Rain", "${env.humidityPct.roundToInt()}% / ${env.precipitationProbPct}%")
                WeatherPill(Icons.Default.WbTwilight, "UV", "${"%.1f".format(env.uvIndex)}")
            }

            if (!env.bestWindowLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElectricGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = ElectricGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = env.bestWindowLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherPill(icon: ImageVector, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMedium)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = VividCyan, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun WellnessCheckInCard(
    latestCheckIn: WellnessCheckInEntity?,
    savedAtMs: Long?,
    onSubmit: (sleep: Int, energy: Int, soreness: Int, stress: Int, hydration: Int, notes: String) -> Unit
) {
    var sleep by remember(latestCheckIn?.id) { mutableIntStateOf(latestCheckIn?.sleepQuality ?: 4) }
    var energy by remember(latestCheckIn?.id) { mutableIntStateOf(latestCheckIn?.energyLevel ?: 4) }
    var soreness by remember(latestCheckIn?.id) { mutableIntStateOf(latestCheckIn?.muscleSoreness ?: 2) }
    var stress by remember(latestCheckIn?.id) { mutableIntStateOf(latestCheckIn?.stressLevel ?: 2) }
    var hydration by remember(latestCheckIn?.id) { mutableIntStateOf(latestCheckIn?.hydrationScore ?: 3) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Wellness Signals",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = latestCheckIn?.let {
                        val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        "Last updated ${fmt.format(Date(it.timestampMs))}"
                    } ?: "Log how your body feels today (1–5)"
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }

                if (savedAtMs != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Saved",
                        tint = ElectricGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            WellnessScaleRow("Sleep Quality", sleep, "Poor", "Deep") { sleep = it }
            WellnessScaleRow("Energy Level", energy, "Low", "Charged") { energy = it }
            WellnessScaleRow("Muscle Soreness", soreness, "Fresh", "Sore") { soreness = it }
            WellnessScaleRow("Mental Stress", stress, "Calm", "High") { stress = it }
            WellnessScaleRow("Hydration", hydration, "Dry", "Optimal") { hydration = it }

            Button(
                onClick = { onSubmit(sleep, energy, soreness, stress, hydration, "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = DeepNavy
                )
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Digital Twin Signals", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WellnessScaleRow(
    label: String,
    value: Int,
    lowHint: String,
    highHint: String,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text("$value / 5 ($lowHint → $highHint)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { score ->
                val selected = score == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) VividCyan.copy(alpha = 0.22f) else NavyMedium)
                        .clickable { onSelect(score) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$score",
                        color = if (selected) VividCyan else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSimulatorCard(
    simInput: SimulationInput,
    outcome: SimulationOutcome?,
    onUpdate: (sport: String, duration: Int, rpe: Int, elevation: Double) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = VividCyan)
                Column {
                    Text(
                        text = "What-If Workout Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Predict strain, fluid loss & Body Battery impact before heading out",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Sport selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CYCLING" to "Cycling", "JOGGING" to "Running", "WALKING" to "Walking").forEach { (id, title) ->
                    val selected = simInput.activityType == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ElectricGreen.copy(alpha = 0.2f) else NavyMedium)
                            .clickable {
                                onUpdate(id, simInput.durationMinutes, simInput.targetRpe, simInput.elevationGainM)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ElectricGreen else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Duration slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Planned Duration", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("${simInput.durationMinutes} min", style = MaterialTheme.typography.labelMedium, color = ElectricGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = simInput.durationMinutes.toFloat(),
                    onValueChange = {
                        onUpdate(simInput.activityType, it.roundToInt(), simInput.targetRpe, simInput.elevationGainM)
                    },
                    valueRange = 15f..180f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
                )
            }

            // Target RPE slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Planned Effort (RPE)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("${simInput.targetRpe} / 10", style = MaterialTheme.typography.labelMedium, color = VividCyan, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = simInput.targetRpe.toFloat(),
                    onValueChange = {
                        onUpdate(simInput.activityType, simInput.durationMinutes, it.roundToInt(), simInput.elevationGainM)
                    },
                    valueRange = 2f..10f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = VividCyan, activeTrackColor = VividCyan)
                )
            }

            if (outcome != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyMedium)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniColumn("Battery After", "${outcome.predictedBatteryAfter}%", readinessColor(outcome.predictedBatteryAfter))
                    MetricMiniColumn("Est. Strain", "${"%.0f".format(outcome.estimatedStrain)}", ElectricGreen)
                    MetricMiniColumn("Hydration", "${outcome.estimatedFluidLossMl} mL", VividCyan)
                    MetricMiniColumn("Recovery", "${outcome.estimatedRecoveryHours}h", TextPrimary)
                }

                Text(
                    text = "${outcome.advisoryNote} (ACWR after: ${"%.2f".format(outcome.predictedAcwrAfter)} • ${outcome.predictedAcwrZone.label})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun RecentSnapshotsCard(snapshots: List<TwinSnapshotEntity>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Recent Twin Timeline",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshots, key = { it.dateKey }) { snap ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(NavyMedium)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = snap.dateKey.takeLast(5),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${snap.readinessScore}",
                            style = MaterialTheme.typography.titleMedium,
                            color = readinessColor(snap.readinessScore),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bat ${snap.bodyBattery}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = VividCyan
                        )
                        Text(
                            text = "Strain ${"%.0f".format(snap.strainScore)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun readinessColor(score: Int): Color = when {
    score >= 75 -> ElectricGreen
    score >= 50 -> VividCyan
    score >= 35 -> WarningAmber
    else -> SpeedRed
}

@Composable
private fun acwrColor(zone: AcwrZone): Color = when (zone) {
    AcwrZone.OPTIMAL -> ElectricGreen
    AcwrZone.DETRAINING, AcwrZone.BUILDING_BASE -> VividCyan
    AcwrZone.ELEVATED_RISK -> WarningAmber
    AcwrZone.OVERTRAINING -> SpeedRed
}

private fun intensityShortLabel(intensity: RecommendedIntensity): String = when (intensity) {
    RecommendedIntensity.REST -> "Rest"
    RecommendedIntensity.RECOVERY_ZONE_1 -> "Zone 1"
    RecommendedIntensity.AEROBIC_ZONE_2 -> "Zone 2"
    RecommendedIntensity.TEMPO_ZONE_3 -> "Zone 3"
    RecommendedIntensity.THRESHOLD_INTERVALS -> "Peak"
}

