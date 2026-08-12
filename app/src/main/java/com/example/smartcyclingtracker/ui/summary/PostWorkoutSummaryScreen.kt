package com.example.smartcyclingtracker.ui.summary

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.R
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.engine.PhysicsEngine
import com.example.smartcyclingtracker.service.RoutePoint
import com.example.smartcyclingtracker.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

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
    onAskPersonalCoach: () -> Unit,
    onBack: () -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .verticalScroll(rememberScrollState())
    ) {
        // Map with drawn route
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RectangleShape)
        ) {
            SummaryMapView(
                routePoints = routePoints,
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
                    text = "Ask AI Coach AI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

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

@Composable
private fun SummaryMapView(
    routePoints: List<RoutePoint>,
    context: Context,
    modifier: Modifier = Modifier
) {
    val lapColors = listOf(
        "#00FF87", // ElectricGreen
        "#FFEB3B", // Yellow
        "#FF5722", // Orange
        "#E91E63", // Pink
        "#03A9F4", // LightBlue
        "#9C27B0"  // Purple
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapRef?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            val colorHex = lapColors[(lap - 1) % lapColors.size]
                            outlinePaint.color = android.graphics.Color.parseColor(colorHex)
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.isAntiAlias = true
                        }
                        mapView.overlays.add(polyline)
                    }
                }

                allGeoPoints.addAll(routePoints.map { GeoPoint(it.lat, it.lng) })

                // Fit map to route bounds
                if (allGeoPoints.size >= 2) {
                    val bbox = BoundingBox.fromGeoPoints(allGeoPoints)
                    mapView.post {
                        mapView.zoomToBoundingBox(bbox.increaseByScale(1.3f), true)
                    }
                } else if (allGeoPoints.isNotEmpty()) {
                    mapView.controller.setCenter(allGeoPoints.first())
                }
                mapView.invalidate()
            }
        }
    )
}

private val CircleShape = RoundedCornerShape(50)
