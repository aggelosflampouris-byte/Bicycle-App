package com.fitnessapp.tracker.ui.summary

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.service.RoutePoint
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.progress.AnimatedLineChart
import com.fitnessapp.tracker.ui.progress.ChartBarData
import com.fitnessapp.tracker.util.GpxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

// ── Lap palette (mirrors the map polyline colors) ─────────────────────────────
private val LAP_COLORS_COMPOSE = listOf(
    Color(0xFF00FF87), // ElectricGreen
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFF5722), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF03A9F4), // LightBlue
    Color(0xFF9C27B0)  // Purple
)

private val LAP_COLORS_HEX = listOf(
    "#00FF87", "#FFEB3B", "#FF5722", "#E91E63", "#03A9F4", "#9C27B0"
)

// ── Per-lap aggregated stats ───────────────────────────────────────────────────
data class LapSummary(
    val lap: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val color: Color
)

/** Compute per-lap summaries from raw route points. */
private fun buildLapSummaries(routePoints: List<RoutePoint>): List<LapSummary> {
    val grouped = routePoints.groupBy { it.lap }
    return grouped.keys.sorted().map { lapNum ->
        val pts = grouped[lapNum] ?: emptyList()

        // Distance: sum haversine between consecutive points
        var dist = 0.0
        for (i in 1 until pts.size) {
            dist += PhysicsEngine.haversineDistance(
                pts[i - 1].lat, pts[i - 1].lng,
                pts[i].lat, pts[i].lng
            )
        }

        // Duration: span between first and last timestamp
        val durationMs = if (pts.size >= 2) pts.last().timestamp - pts.first().timestamp else 0L
        val durationSec = durationMs / 1000L

        // Avg speed in km/h
        val avgSpeed = if (durationSec > 0) (dist / 1000.0) / (durationSec / 3600.0) else 0.0

        LapSummary(
            lap = lapNum,
            distanceMeters = dist,
            durationSeconds = durationSec,
            avgSpeedKmh = avgSpeed,
            color = LAP_COLORS_COMPOSE[(lapNum - 1) % LAP_COLORS_COMPOSE.size]
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PostWorkoutSummaryScreen(
    sessionId: Long,
    onAskPersonalCoach: () -> Unit,
    onBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CompositionLocalProvider(LocalActivityTheme provides (uiState.session?.activityType ?: "CYCLING")) {
        Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ElectricGreen
                    )
                }
                uiState.session != null -> {
                    SummaryContent(
                        session = uiState.session!!,
                        context = context,
                        isGeneratingDebrief = uiState.isGeneratingDebrief,
                        tacticalDebrief = uiState.tacticalDebrief,
                        newAchievements = uiState.newAchievements,
                        onGenerateDebrief = { viewModel.generateTacticalDebrief(null) },
                        onAskPersonalCoach = onAskPersonalCoach,
                        onBack = onBack
                    )
                }
                else -> {
                    Text(
                        text = uiState.error ?: "No data",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryContent(
    session: WorkoutSessionEntity,
    context: Context,
    isGeneratingDebrief: Boolean,
    tacticalDebrief: String?,
    newAchievements: List<com.fitnessapp.tracker.engine.PersonalRecordAchievement>,
    onGenerateDebrief: () -> Unit,
    onAskPersonalCoach: () -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault())
    val gson = remember { Gson() }
    val routePoints: List<RoutePoint> = remember(session.routePointsJson) {
        try {
            val type = object : TypeToken<List<RoutePoint>>() {}.type
            gson.fromJson(session.routePointsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Lap summaries — only meaningful when there are multiple laps
    val lapSummaries: List<LapSummary> = remember(routePoints) {
        buildLapSummaries(routePoints)
    }
    val hasMultipleLaps = lapSummaries.size >= 2

    // Which lap is currently highlighted on the map (null = show all)
    var selectedLap by remember { mutableStateOf<Int?>(null) }

    // GPX Export Launcher
    val gpxExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val gpxData = GpxExporter.generateGpx(session, routePoints)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(gpxData.toByteArray())
                        }
                    }
                    snackbarHostState.showSnackbar("GPX exported successfully!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to export GPX: ${e.message}")
                }
            }
        }
    }

    // Speed Chart Data (downsampled for performance)
    val speedChartData = remember(routePoints) {
        if (routePoints.isEmpty()) emptyList<ChartBarData>()
        else {
            val targetPoints = 40
            val segmentSize = maxOf(1, routePoints.size / targetPoints)
            val segments = routePoints.chunked(segmentSize)
            segments.mapIndexed { index, chunk ->
                val avgSpeed = chunk.map { PhysicsEngine.metersPerSecondToKmh(it.speedMps) }.average().toFloat()
                ChartBarData(
                    label = "", // Keep labels empty to avoid clutter on a continuous line chart
                    value = if (avgSpeed.isNaN()) 0f else avgSpeed
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Map ───────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RectangleShape)
        ) {
            SummaryMapView(
                routePoints = routePoints,
                selectedLap = selectedLap,
                context = context,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
            )
            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.65f to DeepNavy.copy(alpha = 0.85f),
                            1.0f to DeepNavy
                        )
                    )
            )
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(NavyCard.copy(alpha = 0.85f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            // GPX Export button
            if (routePoints.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(session.startTime))
                        gpxExportLauncher.launch("VeloTrack_${session.activityType}_$dateStr.gpx")
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .background(NavyCard.copy(alpha = 0.85f), CircleShape)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Export GPX", tint = TextPrimary)
                }
            }

            // Workout complete badge
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ElectricGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DeepNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "WORKOUT COMPLETE!",
                        color = DeepNavy,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepNavy)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Personal Records Banner ──────────────────────────────────────────
            if (newAchievements.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "🏆 All-Time Personal Records Broken!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            newAchievements.forEach { achievement ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFD700).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(achievement.recordType.icon, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = achievement.recordType.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        Text(
                                            text = achievement.formattedValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFFFD700)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (session.isChallengeCompletion) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "🏆 Challenge Completed!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Congratulations! This workout completed your active daily challenge.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Text(
                text = dateFormat.format(Date(session.startTime)),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Primary stat: distance
            Text(
                text = PhysicsEngine.formatDistance(session.totalDistanceMeters),
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    label = "Duration",
                    value = PhysicsEngine.formatDuration(session.durationSeconds),
                    tint = WarningAmber
                )
                SummaryStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    label = "Avg Speed",
                    value = "${"%.1f".format(session.avgSpeedKmh)} km/h",
                    tint = VividCyan
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Calories",
                    value = "${"%.0f".format(session.caloriesBurned)} kcal",
                    tint = SpeedRed
                )
                SummaryStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Terrain,
                    label = "Elevation",
                    value = "${"%.0f".format(session.elevationGainMeters)} m",
                    tint = ElectricGreen
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ElectricBolt,
                    label = "Watts/kg",
                    value = "${"%.2f".format(session.wattsPerKg)} W/kg",
                    tint = WarningAmber
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            // ── Lap Details Table (only when 2+ laps) ────────────────────────
            if (hasMultipleLaps) {
                LapDetailsTable(
                    laps = lapSummaries,
                    selectedLap = selectedLap,
                    onLapClick = { lapNum ->
                        selectedLap = if (selectedLap == lapNum) null else lapNum
                    }
                )
            }

            // ── Speed Profile Chart ──────────────────────────────────────────
            if (speedChartData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = VividCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Speed Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedLineChart(
                            data = speedChartData,
                            modifier = Modifier.fillMaxWidth(),
                            isArea = true
                        )
                    }
                }
            }

            // ── AI Tactical Debrief Card ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, if (tacticalDebrief != null) VividCyan else GlassBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = VividCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tactical AI Debrief",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        if (tacticalDebrief == null && !isGeneratingDebrief) {
                            Button(
                                onClick = onGenerateDebrief,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VividCyan.copy(alpha = 0.15f),
                                    contentColor = VividCyan
                                )
                            ) {
                                Text("Analyze", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isGeneratingDebrief) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = VividCyan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Qwen is analyzing pace splits and elevation...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    } else if (tacticalDebrief != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tacticalDebrief,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Ask AI Coach button
            Button(
                onClick = onAskPersonalCoach,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyCard,
                    contentColor = ElectricGreen
                ),
                border = BorderStroke(1.5.dp, ElectricGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Ask AI Coach",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    } // Closes main Column

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    } // Closes Box
} // Closes SummaryContent

// ── Lap Details Table ─────────────────────────────────────────────────────────

@Composable
private fun LapDetailsTable(
    laps: List<LapSummary>,
    selectedLap: Int?,
    onLapClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lap Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (selectedLap != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Tap again to clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Column header row
            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lap",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(48.dp),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Distance",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Avg km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)

            // Data rows
            laps.forEach { lap ->
                val isSelected = selectedLap == lap.lap
                val rowBg = if (isSelected) lap.color.copy(alpha = 0.13f) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .clickable { onLapClick(lap.lap) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lap number with colored dot
                    Row(
                        modifier = Modifier.width(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(lap.color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${lap.lap}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) lap.color else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = PhysicsEngine.formatDistance(lap.distanceMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) lap.color else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = PhysicsEngine.formatDuration(lap.durationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) lap.color else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "${"%.1f".format(lap.avgSpeedKmh)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) lap.color else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }

                if (lap.lap != laps.last().lap) {
                    HorizontalDivider(
                        color = GlassBorder.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Map View ──────────────────────────────────────────────────────────────────

@Composable
private fun SummaryMapView(
    routePoints: List<RoutePoint>,
    selectedLap: Int?,
    context: Context,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapRef?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE  -> mapRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapRef?.onPause()
            mapRef?.onDetach()
            mapRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            val osmConfig = org.osmdroid.config.Configuration.getInstance()
            osmConfig.load(
                ctx,
                ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
            )
            osmConfig.userAgentValue =
                "${ctx.packageName}/1.0 (Android; Smart Track cycling app; contact@velotrack.app)"

            val cartoDbTileSource = org.osmdroid.tileprovider.tilesource.XYTileSource(
                "CartoDB-Voyager",
                0, 19, 256, ".png",
                arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"),
                "© OpenStreetMap contributors, © CartoDB"
            )

            MapView(ctx).apply {
                setTileSource(cartoDbTileSource)
                setMultiTouchControls(true)
                isNestedScrollingEnabled = false
                clipToOutline = true
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                controller.setZoom(14.0)
                mapRef = this
            }
        },
        modifier = modifier.clip(RectangleShape),
        update = { mapView ->
            mapView.overlays.clear()
            if (routePoints.isNotEmpty()) {
                val laps = routePoints.groupBy { it.lap }
                val allGeoPoints = mutableListOf<GeoPoint>()
                var prevLapLastPoint: GeoPoint? = null

                laps.keys.sorted().forEach { lap ->
                    val points = laps[lap] ?: emptyList()
                    val geoPoints = mutableListOf<GeoPoint>()
                    if (prevLapLastPoint != null) {
                        geoPoints.add(prevLapLastPoint!!)
                    }
                    geoPoints.addAll(points.map { GeoPoint(it.lat, it.lng) })
                    if (points.isNotEmpty()) {
                        prevLapLastPoint = GeoPoint(points.last().lat, points.last().lng)
                    }

                    if (geoPoints.size >= 2) {
                        val colorHex = LAP_COLORS_HEX[(lap - 1) % LAP_COLORS_HEX.size]
                        val lapColor = android.graphics.Color.parseColor(colorHex)

                        val isSelected   = selectedLap == lap
                        val noneSelected = selectedLap == null
                        val fullyVisible = noneSelected || isSelected

                        // White glow stroke under the selected lap line
                        if (isSelected) {
                            val glowPolyline = Polyline().apply {
                                setPoints(geoPoints)
                                outlinePaint.color = android.graphics.Color.WHITE
                                outlinePaint.alpha = 160
                                outlinePaint.strokeWidth = 20f
                                outlinePaint.isAntiAlias = true
                            }
                            mapView.overlays.add(glowPolyline)
                        }

                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            outlinePaint.color = lapColor
                            outlinePaint.alpha = if (fullyVisible) 255 else 55
                            outlinePaint.strokeWidth = when {
                                isSelected   -> 13f
                                noneSelected -> 10f
                                else         -> 6f
                            }
                            outlinePaint.isAntiAlias = true
                        }
                        mapView.overlays.add(polyline)
                    }
                }

                allGeoPoints.addAll(routePoints.map { GeoPoint(it.lat, it.lng) })

                // Zoom to the selected lap's area, or the full route when none selected
                val zoomPoints: List<GeoPoint> = if (selectedLap != null) {
                    (laps[selectedLap] ?: emptyList()).map { GeoPoint(it.lat, it.lng) }
                } else {
                    allGeoPoints
                }

                if (zoomPoints.size >= 2) {
                    val bbox = BoundingBox.fromGeoPoints(zoomPoints)
                    mapView.post {
                        mapView.zoomToBoundingBox(bbox.increaseByScale(1.4f), true)
                    }
                } else if (zoomPoints.isNotEmpty()) {
                    mapView.controller.setCenter(zoomPoints.first())
                }
                mapView.invalidate()
            }
        }
    )
}

private val CircleShape = RoundedCornerShape(50)
