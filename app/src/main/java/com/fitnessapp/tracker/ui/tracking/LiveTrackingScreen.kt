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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog
import com.fitnessapp.tracker.util.TtsManager
import com.fitnessapp.tracker.util.VoiceInteractionManager
import com.fitnessapp.tracker.util.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val isTracking by remember(viewModel.trackingState) { viewModel.trackingState.map { it.isTracking }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    val isPaused by remember(viewModel.trackingState) { viewModel.trackingState.map { it.isPaused }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    val currentLap by remember(viewModel.trackingState) { viewModel.trackingState.map { it.currentLap }.distinctUntilChanged() }.collectAsStateWithLifecycle(1)
    val lastSavedSessionId by remember(viewModel.trackingState) { viewModel.trackingState.map { it.lastSavedSessionId }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val coachLanguage by viewModel.coachLanguage.collectAsStateWithLifecycle()
    val isVoiceCoachingEnabled by viewModel.isVoiceCoachingEnabled.collectAsStateWithLifecycle()
    val inFlightState by viewModel.inFlightState.collectAsStateWithLifecycle()
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()

    val voiceManager = remember { VoiceInteractionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()
    val ttsManager = remember { TtsManager(context) }

    LaunchedEffect(coachLanguage) {
        ttsManager.setCoachLanguage(coachLanguage)
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
            ttsManager.shutdown()
        }
    }

    val emergencyContactName by viewModel.emergencyContactName.collectAsStateWithLifecycle()
    val emergencyContactPhone by viewModel.emergencyContactPhone.collectAsStateWithLifecycle()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(coachLanguage)
        }
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadGpxRoute(context, it) }
    }

    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Success -> {
                viewModel.askInFlightCoach(state.recognizedText)
                voiceManager.resetState()
            }
            else -> {}
        }
    }

    LaunchedEffect(inFlightState.response) {
        val reply = inFlightState.response
        if (!reply.isNullOrBlank() && isVoiceCoachingEnabled) {
            ttsManager.speak(reply, flushQueue = true)
        }
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
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
        if (hasLocationPermission && isGpsEnabled && !isTracking) {
            viewModel.startTracking(context, activityType)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                if (hasLocationPermission && isGpsEnabled && !isTracking) {
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
    var lastProcessedIndex by remember { mutableStateOf(0) }

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
                                outlinePaint.color = android.graphics.Color.parseColor(colorHex)
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
                    outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
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

    // Ghost Rider Marker on Map
    var ghostMarkerRef by remember { mutableStateOf<org.osmdroid.views.overlay.Marker?>(null) }
    LaunchedEffect(trackingState.ghostPacerState, mapViewRef) {
        val ghost = trackingState.ghostPacerState
        mapViewRef?.let { map ->
            if (ghost.isActive && ghost.ghostLat != 0.0) {
                val marker = ghostMarkerRef ?: org.osmdroid.views.overlay.Marker(map).apply {
                    title = "👻 Ghost Rider (${ghost.ghostSpeedKmh.toInt()} km/h)"
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                    ghostMarkerRef = this
                }
                marker.position = GeoPoint(ghost.ghostLat, ghost.ghostLng)
                map.invalidate()
            } else {
                ghostMarkerRef?.let {
                    map.overlays.remove(it)
                    ghostMarkerRef = null
                    map.invalidate()
                }
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
                            imageVector = if (trackingState.activeGpxRoute != null) Icons.Default.AltRoute else Icons.Default.FileUpload,
                            contentDescription = "Import GPX Route",
                            tint = if (trackingState.activeGpxRoute != null) VividCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Hands-free in-flight AI voice coach button
                    val isListening = voiceState is VoiceState.Listening || voiceState is VoiceState.Recognizing
                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    voiceManager.startListening(coachLanguage)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isListening) SpeedRed.copy(alpha = 0.3f) else NavyCard)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Ask AI Coach",
                            tint = if (isListening) SpeedRed else ElectricGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // In-flight AI response or listening banner
            if (voiceState is VoiceState.Listening || voiceState is VoiceState.Recognizing || inFlightState.isLoading || inFlightState.response != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, if (voiceState is VoiceState.Listening) SpeedRed else ElectricGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (inFlightState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ElectricGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    voiceState is VoiceState.Listening -> "🎙️ Listening... (Ask anything about your pace/route)"
                                    voiceState is VoiceState.Recognizing -> "🧠 Processing speech..."
                                    inFlightState.isLoading -> "🤖 AI Coach Analyzing Live Telemetry..."
                                    else -> inFlightState.query ?: "AI Coach Briefing"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (voiceState is VoiceState.Listening) SpeedRed else TextSecondary
                            )
                            if (!inFlightState.response.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = inFlightState.response!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                        if (inFlightState.response != null) {
                            IconButton(
                                onClick = { viewModel.dismissInFlightResponse() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
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
                                when (activityType) {
                                    "WALKING" -> "Turn on Location Services to track your walk."
                                    "JOGGING" -> "Turn on Location Services to track your jog."
                                    else -> "Turn on Location Services to track your ride."
                                },
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

            // ClimbPro Live Ascent HUD Card
            val liveClimb = trackingState.liveClimb
            if (liveClimb != null) {
                com.fitnessapp.tracker.ui.components.ClimbProHUDCard(liveClimb = liveClimb)
            }

            // Ghost Rider Virtual Pacer HUD Card
            if (trackingState.ghostPacerState.isActive) {
                com.fitnessapp.tracker.ui.components.GhostPacerHUDCard(ghostState = trackingState.ghostPacerState)
            }

            // Live Segment Sprint HUD Card
            if (trackingState.liveSegmentStatus.activeSegment != null) {
                com.fitnessapp.tracker.ui.components.SegmentSprintHUDCard(status = trackingState.liveSegmentStatus)
            }

            // Live Challenge Real-time Completion HUD
            val activeChallenge = trackingState.activeChallenge
            if (activeChallenge != null && activeChallenge.activityType == activityType) {
                LiveChallengeProgressHUD(
                    challenge = activeChallenge,
                    trackingState = trackingState,
                    elapsedSeconds = elapsedSeconds
                )
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
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = VividCyan),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
                                    fontWeight = FontWeight.ExtraBold
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

        // Emergency SOS Crash Dialog
        com.fitnessapp.tracker.ui.components.EmergencySosDialog(
            crashState = trackingState.crashState,
            emergencyContactName = emergencyContactName,
            emergencyContactPhone = emergencyContactPhone,
            onCancel = { viewModel.cancelSos() }
        )

        // Finish Workout Confirmation Dialog
        if (showFinishDialog) {
            FinishWorkoutDialog(
                activityType = activityType,
                distanceMeters = viewModel.trackingState.value.distanceMeters,
                elapsedSeconds = viewModel.elapsedSeconds.value,
                calories = viewModel.trackingState.value.calories,
                onResume = { showFinishDialog = false },
                onDiscard = {
                    showDiscardConfirmDialog = true
                },
                onSaveAndFinish = {
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
                text = "LAP $currentLap",
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
                    when (activityType) {
                        "WALKING" -> "Great walk! Are you ready to save your workout summary and get feedback from AI Coach AI?"
                        "JOGGING" -> "Great jog! Are you ready to save your workout summary and get feedback from AI Coach AI?"
                        else -> "Great ride! Are you ready to save your workout summary and get feedback from AI Coach AI?"
                    },
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

private data class LiveChallengeProgress(
    val currentVal: Double,
    val targetVal: Double,
    val unit: String,
    val formattedCurrent: String,
    val formattedTarget: String,
    val remainingText: String,
    val isCompleted: Boolean
)

@Composable
private fun LiveChallengeProgressHUD(
    challenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity,
    trackingState: com.fitnessapp.tracker.service.TrackingState,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier
) {
    val metric = challenge.metric
    val target = challenge.targetValue

    val info = when (metric) {
        com.fitnessapp.tracker.data.local.entity.ChallengeMetric.DISTANCE -> {
            val totalMeters = challenge.currentProgress + trackingState.distanceMeters
            val currentKm = totalMeters / 1000.0
            val targetKm = target / 1000.0
            val remainingMeters = (target - totalMeters).coerceAtLeast(0.0)
            val remainingKm = remainingMeters / 1000.0
            val done = totalMeters >= target
            val remText = if (done) "Goal Achieved!" else "${"%.2f".format(remainingKm)} km to go"
            LiveChallengeProgress(
                currentVal = totalMeters,
                targetVal = target,
                unit = "km",
                formattedCurrent = "%.2f".format(currentKm),
                formattedTarget = "%.2f".format(targetKm),
                remainingText = remText,
                isCompleted = done
            )
        }
        com.fitnessapp.tracker.data.local.entity.ChallengeMetric.SPEED -> {
            val avgSpeed = if (elapsedSeconds > 0) (trackingState.distanceMeters / 1000.0) / (elapsedSeconds / 3600.0) else trackingState.speedKmh
            val done = avgSpeed >= target && elapsedSeconds >= 30
            val remSpeed = (target - avgSpeed).coerceAtLeast(0.0)
            val remText = if (done) "Target Speed Maintained!" else "+${"%.1f".format(remSpeed)} km/h needed"
            LiveChallengeProgress(
                currentVal = avgSpeed,
                targetVal = target,
                unit = "km/h",
                formattedCurrent = "%.1f".format(avgSpeed),
                formattedTarget = "%.1f".format(target),
                remainingText = remText,
                isCompleted = done
            )
        }
        com.fitnessapp.tracker.data.local.entity.ChallengeMetric.CALORIES -> {
            val totalCals = challenge.currentProgress + trackingState.calories
            val done = totalCals >= target
            val remainingCals = (target - totalCals).coerceAtLeast(0.0)
            val remText = if (done) "Calorie Goal Crushed!" else "${"%.0f".format(remainingCals)} kcal to go"
            LiveChallengeProgress(
                currentVal = totalCals,
                targetVal = target,
                unit = "kcal",
                formattedCurrent = "%.0f".format(totalCals),
                formattedTarget = "%.0f".format(target),
                remainingText = remText,
                isCompleted = done
            )
        }
    }

    val progressFraction = if (info.targetVal > 0) (info.currentVal / info.targetVal).coerceIn(0.0, 1.0).toFloat() else 1.0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "challengeProgress"
    )
    val percentage = (progressFraction * 100).toInt()

    val borderColor = if (info.isCompleted) Color(0xFFFFD700) else VividCyan.copy(alpha = 0.6f)
    val containerBg = if (info.isCompleted) NavyCard.copy(alpha = 0.95f) else NavyCard.copy(alpha = 0.90f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (info.isCompleted) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (info.isCompleted) 8.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (info.isCompleted) Color(0xFFFFD700).copy(alpha = 0.2f) else VividCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (info.isCompleted) "🏆" else "🏅",
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (info.isCompleted) "CHALLENGE COMPLETED!" else "${challenge.period.name} ${challenge.metric.name} CHALLENGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (info.isCompleted) Color(0xFFFFD700) else VividCyan,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = info.remainingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (info.isCompleted) ElectricGreen else TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Live Percentage Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (info.isCompleted) Color(0xFFFFD700).copy(alpha = 0.2f) else DeepNavy)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = if (info.isCompleted) Color(0xFFFFD700) else ElectricGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Gradient Progress Bar
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
                                colors = if (info.isCompleted) {
                                    listOf(Color(0xFFFFD700), Color(0xFFFFA500), ElectricGreen)
                                } else {
                                    listOf(VividCyan, ElectricGreen)
                                }
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Live progress numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Progress: ${info.formattedCurrent} / ${info.formattedTarget} ${info.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (info.isCompleted) {
                    Text(
                        text = "🎉 Done! Keep Pushing!",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
