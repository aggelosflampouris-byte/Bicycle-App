package com.fitnessapp.tracker.ui.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.safety.CrashState
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog
import com.fitnessapp.tracker.util.TtsManager
import kotlin.math.abs
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
    activityType: String = "CYCLING",
    onTrackingFinished: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activityNoun = ActivityType.fromString(activityType).nounSingular
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val isTracking = trackingState.isTracking
    val isPaused = trackingState.isPaused
    val currentLap = trackingState.currentLap
    val lastSavedSessionId = trackingState.lastSavedSessionId
    val audioLanguage by viewModel.audioLanguage.collectAsStateWithLifecycle()
    val isAudioCuesEnabled by viewModel.isAudioCuesEnabled.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()

    val ttsManager = remember { TtsManager(context) }

    LaunchedEffect(audioLanguage) {
        ttsManager.setAudioLanguage(audioLanguage)
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    val emergencyContactName by viewModel.emergencyContactName.collectAsStateWithLifecycle()
    val emergencyContactPhone by viewModel.emergencyContactPhone.collectAsStateWithLifecycle()

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadGpxRoute(context, it) }
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var hasExitedTracking by remember { mutableStateOf(false) }
    var lastCompletedLapMessage by remember { mutableStateOf<String?>(null) }
    var lastSeenLap by remember { mutableIntStateOf(1) }

    LaunchedEffect(currentLap) {
        if (currentLap > lastSeenLap) {
            val completedLap = currentLap - 1
            lastCompletedLapMessage = "🏁 Lap $completedLap Completed! Starting Lap $currentLap"
            lastSeenLap = currentLap
            delay(3500L)
            lastCompletedLapMessage = null
        } else {
            lastSeenLap = currentLap
        }
    }

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
        val liveState = viewModel.trackingState.value
        if (hasLocationPermission && isGpsEnabled && !liveState.isTracking && !hasExitedTracking && liveState.lastSavedSessionId == null) {
            viewModel.startTracking(context, activityType)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val liveState = viewModel.trackingState.value
                if (hasLocationPermission && isGpsEnabled && !liveState.isTracking && !hasExitedTracking && liveState.lastSavedSessionId == null) {
                    viewModel.startTracking(context, activityType)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-start tracking is handled by ON_RESUME observer and permission launcher callback

    // Track route on map from Service StateFlow
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    val lapColors = listOf(
        "#00FF87", // ElectricGreen
        "#FFEB3B", // Yellow
        "#FF5722", // Orange
        "#E91E63", // Pink
        "#03A9F4", // LightBlue
        "#9C27B0"  // Purple
    )

    // Update map with GPS points incrementally
    val lapPolylines = remember { mutableMapOf<Int, Polyline>() }
    var lastProcessedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.routePoints.collect { routePoints ->
            if (routePoints.size < lastProcessedIndex || routePoints.isEmpty()) {
                lastProcessedIndex = 0
                lapPolylines.clear()
                mapViewRef?.overlays?.removeIf { it is Polyline }
                mapViewRef?.invalidate()
            }

            if (routePoints.size > lastProcessedIndex) {
                mapViewRef?.let { map ->
                    for (i in lastProcessedIndex until routePoints.size) {
                        val point = routePoints[i]
                        val lap = point.lap
                        
                        val polyline = lapPolylines.getOrPut(lap) {
                            Polyline().apply {
                                val colorHex = lapColors[(lap - 1) % lapColors.size]
                                outlinePaint.color = colorHex.toColorInt()
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.isAntiAlias = true
                                map.overlays.add(this)
                            }
                        }
                        
                        // Seamless lap transition: add the last point of previous lap to this lap's polyline
                        if (i > 0 && routePoints[i - 1].lap != lap) {
                            val prev = routePoints[i - 1]
                            polyline.addPoint(GeoPoint(prev.lat, prev.lng))
                        }
                        
                        polyline.addPoint(GeoPoint(point.lat, point.lng))
                    }
                    lastProcessedIndex = routePoints.size
                    
                    val lastPoint = routePoints.last()
                    map.controller.animateTo(GeoPoint(lastPoint.lat, lastPoint.lng))
                    map.invalidate()
                }
            }
        }
    }

    // Draw imported GPX route on map
    var gpxPolylineRef by remember { mutableStateOf<Polyline?>(null) }
    LaunchedEffect(trackingState.activeGpxRoute, mapViewRef) {
        val route = trackingState.activeGpxRoute
        mapViewRef?.let { map ->
            gpxPolylineRef?.let { map.overlays.remove(it) }
            if (route != null && route.points.isNotEmpty()) {
                val poly = Polyline().apply {
                    outlinePaint.color = "#00E5FF".toColorInt()
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.isAntiAlias = true
                    for (pt in route.points) {
                        addPoint(GeoPoint(pt.lat, pt.lng))
                    }
                }
                gpxPolylineRef = poly
                map.overlays.add(0, poly)
                map.invalidate()
            }
        }
    }

    // Navigate to summary once session is saved
    LaunchedEffect(lastSavedSessionId) {
        lastSavedSessionId?.let { id ->
            viewModel.clearLastSavedSessionId()
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            .background(if (isPaused) WarningAmber else ElectricGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPaused) "PAUSED" else "LIVE TRACKING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) WarningAmber else ElectricGreen
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // GPX Route Import / Toggle button
                    IconButton(
                        onClick = {
                            if (trackingState.activeGpxRoute != null) {
                                viewModel.clearGpxRoute()
                            } else {
                                gpxPickerLauncher.launch("*/*")
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (trackingState.activeGpxRoute != null) VividCyan.copy(alpha = 0.25f) else NavyCard)
                    ) {
                        Icon(
                            imageVector = if (trackingState.activeGpxRoute != null) Icons.AutoMirrored.Filled.AltRoute else Icons.Default.FileUpload,
                            contentDescription = "Import GPX Route",
                            tint = if (trackingState.activeGpxRoute != null) VividCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Spoken Audio Cues Mute / Unmute Button
                    IconButton(
                        onClick = {
                            if (isAudioCuesEnabled) {
                                ttsManager.stop()
                            }
                            viewModel.toggleAudioCues()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (!isAudioCuesEnabled) SpeedRed.copy(alpha = 0.2f) else NavyCard)
                    ) {
                        Icon(
                            imageVector = if (isAudioCuesEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (isAudioCuesEnabled) "Mute Audio Cues" else "Unmute Audio Cues",
                            tint = if (isAudioCuesEnabled) ElectricGreen else SpeedRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
                                val perms = buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        add(Manifest.permission.ACTIVITY_RECOGNITION)
                                    }
                                }.toTypedArray()
                                permissionLauncher.launch(perms)
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
                                "Turn on Location Services to track your $activityNoun.",
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

            // Off-Route Warning Alert Banner
            val offRouteStatus = trackingState.offRouteStatus
            if (offRouteStatus?.isOffRoute == true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, WarningAmber),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                        Text(
                            text = "⚠️ Off Route by ${offRouteStatus.distanceToRouteMeters.toInt()}m! Follow the cyan track line.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                }
            }

            // Compact Live Climb HUD chip
            val liveClimb = trackingState.liveClimb
            if (liveClimb != null) {
                val climbText = if (liveClimb.isAscending) {
                    "${liveClimb.climb.category} • ${"%.1f".format(liveClimb.climb.avgGradientPct)}% • ${liveClimb.distanceToSummitMeters.toInt()}m (${liveClimb.progressPct}%)"
                } else {
                    "${liveClimb.climb.category} in ${liveClimb.distanceToStartMeters.toInt()}m"
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Terrain, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                        Text(
                            text = climbText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber,
                            maxLines = 1
                        )
                    }
                }
            }

            // Active Digital Twin Voice/Sensor Nudge Banner
            trackingState.activeTwinNudge?.let { nudgeMsg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = VividCyan.copy(alpha = 0.16f)),
                    border = BorderStroke(1.dp, VividCyan),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = VividCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = nudgeMsg,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { viewModel.dismissTwinNudge() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss nudge", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Compact Live Digital Twin Telemetry & 1-Tap Effort Tag Bar
            val hasWeather = !trackingState.currentWeatherSummary.isNullOrBlank()
            val hasCadence = trackingState.liveCadenceSpm > 0.0
            val stability = trackingState.gaitStabilityScore
            if (isTracking) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasWeather || hasCadence || stability != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            trackingState.currentWeatherSummary?.let { weather ->
                                TwinStatusPill(
                                    label = weather,
                                    tint = VividCyan
                                )
                            }
                            if (hasCadence) {
                                TwinStatusPill(
                                    label = "${"%.0f".format(trackingState.liveCadenceSpm)} spm",
                                    tint = ElectricGreen
                                )
                            }
                            stability?.let { score ->
                                val tint = if (score >= 70.0) ElectricGreen else WarningAmber
                                TwinStatusPill(
                                    label = "Form ${"%.0f".format(score)}%",
                                    tint = tint
                                )
                            }
                            if (trackingState.isLowLightWarning) {
                                TwinStatusPill(
                                    label = "Low Light",
                                    tint = WarningAmber
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Effort:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        val rpeOptions = listOf(
                            3 to "Easy",
                            5 to "Steady",
                            7 to "Hard",
                            9 to "Max"
                        )
                        rpeOptions.forEach { (rpeVal, label) ->
                            val selected = trackingState.liveRpeTag == rpeVal
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setLiveRpe(if (selected) null else rpeVal)
                                    },
                                color = if (selected) ElectricGreen.copy(alpha = 0.22f) else NavyCard,
                                border = BorderStroke(1.dp, if (selected) ElectricGreen else GlassBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) ElectricGreen else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Top stats panel
            TrackingStatsPanel(
                stateProvider = { viewModel.trackingState.value },
                elapsedSecondsFlow = viewModel.elapsedSeconds,
                trackingStateFlow = viewModel.trackingState,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // Map view section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(NavyCard)
            ) {
                OsmMapView(
                    context = context,
                    onMapReady = { mapViewRef = it }
                )

                // Lap completion banner overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .zIndex(20f)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = lastCompletedLapMessage != null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = VividCyan),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, DeepNavy)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = DeepNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = lastCompletedLapMessage ?: "",
                                    color = DeepNavy,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Auto-pause overlay
                if (isPaused) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = WarningAmber.copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(8.dp)
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Action Control Bar
            TrackingControlBar(
                isPaused = isPaused,
                currentLap = currentLap,
                onTogglePause = { viewModel.togglePause(context) },
                onLapClick = {
                    val completedLap = currentLap
                    viewModel.markLap(context)
                    Toast.makeText(
                        context,
                        "Lap $completedLap completed! Starting Lap ${completedLap + 1}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFinishClick = { showFinishDialog = true }
            )
        }


        // Finish Workout Confirmation Dialog
        if (showFinishDialog) {
            FinishWorkoutDialog(
                activityType = activityType,
                distanceMeters = trackingState.distanceMeters,
                elapsedSeconds = elapsedSeconds,
                calories = trackingState.calories,
                onResume = { showFinishDialog = false },
                onDiscard = {
                    showDiscardConfirmDialog = true
                },
                onSaveAndFinish = {
                    hasExitedTracking = true
                    showFinishDialog = false
                    viewModel.stopTracking(context)
                }
            )
        }

        // Discard Workout Warning Confirmation Dialog
        if (showDiscardConfirmDialog) {
            DeleteConfirmationDialog(
                title = "Discard Workout?",
                message = "Are you sure you want to discard this workout session? All recorded data and progress for this workout will be permanently deleted and cannot be recovered.",
                confirmButtonText = "Discard",
                onConfirm = {
                    hasExitedTracking = true
                    showDiscardConfirmDialog = false
                    showFinishDialog = false
                    viewModel.discardTracking(context)
                    if (onBack != null) onBack() else onTrackingFinished(-1L)
                },
                onDismiss = {
                    showDiscardConfirmDialog = false
                }
            )
        }

        // Interactive Crash SOS Emergency Overlay
        val crashState = trackingState.crashState
        if (crashState is CrashState.ImpactDetected ||
            crashState is CrashState.Countdown ||
            crashState is CrashState.SosDispatched
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .zIndex(50f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(2.dp, SpeedRed)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SpeedRed,
                            modifier = Modifier.size(48.dp)
                        )
                        val titleText = when (crashState) {
                            is CrashState.ImpactDetected -> "Crash Impact Detected"
                            is CrashState.Countdown -> "Emergency SOS in ${crashState.secondsRemaining}s"
                            is CrashState.SosDispatched -> "Emergency SOS Sent"
                            else -> ""
                        }
                        val subtitleText = when (crashState) {
                            is CrashState.ImpactDetected -> "Monitoring movement before starting the SOS countdown."
                            is CrashState.Countdown -> {
                                val target = emergencyContactPhone.ifBlank { emergencyContactName.ifBlank { "emergency contact" } }
                                "An emergency SMS with your GPS coordinates will be sent to $target unless cancelled."
                            }
                            is CrashState.SosDispatched -> "Distress SMS dispatched to ${crashState.contactPhone.ifBlank { "emergency contact" }}."
                            else -> ""
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SpeedRed,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.cancelSos() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (crashState is CrashState.SosDispatched) VividCyan else ElectricGreen,
                                contentColor = DeepNavy
                            )
                        ) {
                            Text(
                                text = if (crashState is CrashState.SosDispatched) "DISMISS ALERT" else "I'M OK — CANCEL SOS",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwinStatusPill(
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackingControlBar(
    isPaused: Boolean,
    currentLap: Int = 1,
    onTogglePause: () -> Unit,
    onLapClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDarker)
            .navigationBarsPadding()
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
                fontWeight = FontWeight.SemiBold
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
                text = "LAP $currentLap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FinishWorkoutDialog(
    activityType: String,
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
        shape = RoundedCornerShape(8.dp),
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
                    "Great ${ActivityType.fromString(activityType).nounSingular}! Are you ready to save your workout summary and Digital Twin recovery analysis?",
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
fun TrackingStatsPanel(
    stateProvider: () -> com.fitnessapp.tracker.service.TrackingState,
    elapsedSecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    trackingStateFlow: kotlinx.coroutines.flow.StateFlow<com.fitnessapp.tracker.service.TrackingState>,
    modifier: Modifier = Modifier
) {
    val state by trackingStateFlow.collectAsStateWithLifecycle()
    val speedKmh = state.speedKmh
    val distanceMeters = state.distanceMeters
    val calories = state.calories
    val elevationGain = state.elevationGainMeters
    val isPaused = state.isPaused

    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Big speed display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PhysicsEngine.formatSpeed(speedKmh),
                style = MaterialTheme.typography.displayLarge,
                color = if (isPaused) TextSecondary else ElectricGreen,
                fontWeight = FontWeight.Bold,
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
            TrackingStatItem(
                label = "Elevation",
                value = "${"%.0f".format(elevationGain)}m",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = com.fitnessapp.tracker.theme.ElectricGreen
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
            mapRef?.onPause()
            mapRef?.onDetach()
            mapRef = null
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
                "${ctx.packageName}/1.0 (Android; Smart Track outdoor workout app)"

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
        modifier = Modifier.fillMaxSize().clip(RectangleShape),
        update = { map -> 
            // Map updates handled via LaunchedEffect for route drawing
        }
    )
}
