package com.example.smartcyclingtracker.ui.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.engine.PhysicsEngine
import com.example.smartcyclingtracker.theme.*
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun LiveTrackingScreen(
    onTrackingFinished: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.trackingState.collectAsStateWithLifecycle()

    var showFinishDialog by remember { mutableStateOf(false) }

    // Check location permission state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    var isGpsEnabled by remember {
        mutableStateOf(locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission && isGpsEnabled && !state.isTracking) {
            viewModel.startTracking(context)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                if (hasLocationPermission && isGpsEnabled && !state.isTracking) {
                    viewModel.startTracking(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-start tracking if not active and permission is granted and GPS is on
    LaunchedEffect(hasLocationPermission, isGpsEnabled) {
        if (hasLocationPermission && isGpsEnabled && !state.isTracking) {
            viewModel.startTracking(context)
        }
    }

    // Track route on map, storing GeoPoint and its lap number
    val routePoints = remember { mutableStateListOf<Pair<GeoPoint, Int>>() }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    val lapColors = listOf(
        "#00FF87", // ElectricGreen
        "#FFEB3B", // Yellow
        "#FF5722", // Orange
        "#E91E63", // Pink
        "#03A9F4", // LightBlue
        "#9C27B0"  // Purple
    )

    // Update map with new GPS points
    LaunchedEffect(state.currentLat, state.currentLng) {
        if (state.currentLat != 0.0 && state.currentLng != 0.0) {
            val point = GeoPoint(state.currentLat, state.currentLng)
            routePoints.add(Pair(point, state.currentLap))
            mapViewRef?.let { map ->
                map.overlays.removeIf { it is Polyline }
                
                // Group points by lap
                val laps = routePoints.groupBy { it.second }
                
                laps.forEach { (lap, points) ->
                    if (points.size >= 2) {
                        val polyline = Polyline().apply {
                            setPoints(points.map { it.first })
                            val colorHex = lapColors[(lap - 1) % lapColors.size]
                            outlinePaint.color = android.graphics.Color.parseColor(colorHex)
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.isAntiAlias = true
                        }
                        map.overlays.add(polyline)
                    }
                }
                
                map.controller.animateTo(point)
                map.invalidate()
            }
        }
    }

    // Navigate to summary once session is saved
    LaunchedEffect(state.lastSavedSessionId) {
        state.lastSavedSessionId?.let { id ->
            onTrackingFinished(id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with back/minimize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NavyCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Menu",
                            tint = TextPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.isPaused) WarningAmber else ElectricGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isPaused) "PAUSED" else "LIVE TRACKING",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isPaused) WarningAmber else ElectricGreen
                    )
                }

                Spacer(modifier = Modifier.size(40.dp))
            }

            // Permission Warning Banner if GPS not allowed
            if (!hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, WarningAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Location Permission Required",
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Enable GPS to record speed, route & distance.",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepNavy),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!isGpsEnabled) {
                // GPS Disabled Warning Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SpeedRed.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, SpeedRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "GPS is Disabled",
                                color = SpeedRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Turn on Location Services to track your ride.",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpeedRed, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top stats panel
            TrackingStatsPanel(
                speedKmh = state.speedKmh,
                distanceMeters = state.distanceMeters,
                elapsedSeconds = state.elapsedSeconds,
                calories = state.calories,
                isPaused = state.isPaused,
                modifier = Modifier.weight(1f)
            )

            // Map view section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(NavyCard)
            ) {
                OsmMapView(
                    context = context,
                    onMapReady = { mapViewRef = it }
                )

                // Auto-pause overlay
                if (state.isPaused) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = WarningAmber.copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = DeepNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WORKOUT PAUSED",
                                color = DeepNavy,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Bottom Action Control Bar
            TrackingControlBar(
                isPaused = state.isPaused,
                onTogglePause = { viewModel.togglePause(context) },
                onLapClick = { viewModel.markLap(context) },
                onFinishClick = { showFinishDialog = true }
            )
        }

        // Finish Ride Confirmation Dialog
        if (showFinishDialog) {
            FinishRideDialog(
                distanceMeters = state.distanceMeters,
                elapsedSeconds = state.elapsedSeconds,
                calories = state.calories,
                onResume = { showFinishDialog = false },
                onDiscard = {
                    showFinishDialog = false
                    viewModel.discardTracking(context)
                    if (onBack != null) onBack() else onTrackingFinished(-1L)
                },
                onSaveAndFinish = {
                    showFinishDialog = false
                    viewModel.stopTracking(context)
                }
            )
        }
    }
}

@Composable
private fun TrackingControlBar(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onLapClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDarker)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pause / Resume Button
        Button(
            onClick = onTogglePause,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) ElectricGreen else WarningAmber,
                contentColor = DeepNavy
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isPaused) "RESUME" else "PAUSE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Lap Button
        Button(
            onClick = onLapClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VividCyan,
                contentColor = DeepNavy
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "LAP",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Finish / Stop Button
        Button(
            onClick = onFinishClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpeedRed,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "FINISH",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun FinishRideDialog(
    distanceMeters: Double,
    elapsedSeconds: Long,
    calories: Double,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onSaveAndFinish: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onResume,
        containerColor = NavyCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Finish Workout?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Great ride! Are you ready to save your workout summary and get feedback from VeloCoach AI?",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepNavy)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(PhysicsEngine.formatDistance(distanceMeters), color = VividCyan, fontWeight = FontWeight.Bold)
                        Text("Distance", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(PhysicsEngine.formatDuration(elapsedSeconds), color = WarningAmber, fontWeight = FontWeight.Bold)
                        Text("Time", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${"%.0f".format(calories)} kcal", color = SpeedRed, fontWeight = FontWeight.Bold)
                        Text("Calories", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveAndFinish,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save & Finish", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = SpeedRed)
                }
                TextButton(onClick = onResume) {
                    Text("Resume", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun TrackingStatsPanel(
    speedKmh: Double,
    distanceMeters: Double,
    elapsedSeconds: Long,
    calories: Double,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        // Big speed display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PhysicsEngine.formatSpeed(speedKmh),
                style = MaterialTheme.typography.displayLarge,
                color = if (isPaused) TextSecondary else ElectricGreen,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

        // Secondary stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TrackingStatItem(
                label = "Distance",
                value = PhysicsEngine.formatDistance(distanceMeters),
                icon = Icons.Default.Route,
                tint = VividCyan
            )
            TrackingStatItem(
                label = "Time",
                value = PhysicsEngine.formatDuration(elapsedSeconds),
                icon = Icons.Default.Timer,
                tint = WarningAmber
            )
            TrackingStatItem(
                label = "Calories",
                value = "${"%.0f".format(calories)}",
                icon = Icons.Default.LocalFireDepartment,
                tint = SpeedRed
            )
        }
    }
}

@Composable
private fun TrackingStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun OsmMapView(
    context: Context,
    onMapReady: (MapView) -> Unit
) {
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
            val osmConfig = Configuration.getInstance()
            osmConfig.load(
                ctx,
                ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
            )
            osmConfig.userAgentValue =
                "${ctx.packageName}/1.0 (Android; VeloTrack cycling app; contact@velotrack.app)"

            val cartoDbTileSource = org.osmdroid.tileprovider.tilesource.XYTileSource(
                "CartoDB-Voyager",
                0, 19, 256, ".png",
                arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"),
                "© OpenStreetMap contributors, © CartoDB"
            )

            MapView(ctx).apply {
                setTileSource(cartoDbTileSource)
                setMultiTouchControls(true)
                controller.setZoom(16.5)
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                val myLocationOverlay = MyLocationNewOverlay(
                    GpsMyLocationProvider(ctx), this
                ).apply {
                    enableMyLocation()
                    enableFollowLocation()
                }
                overlays.add(myLocationOverlay)
                mapRef = this
                onMapReady(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { map -> 
            // Map updates handled via LaunchedEffect for route drawing
        }
    )
}
