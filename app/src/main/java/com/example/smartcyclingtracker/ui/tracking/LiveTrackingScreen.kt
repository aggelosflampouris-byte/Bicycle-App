package com.example.smartcyclingtracker.ui.tracking

import android.content.Context
import android.view.MotionEvent
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
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.trackingState.collectAsStateWithLifecycle()

    var showStopConfirm by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableStateOf(0f) }
    var isLongPressing by remember { mutableStateOf(false) }

    // Track route on map
    val routePoints = remember { mutableStateListOf<GeoPoint>() }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Update map with new GPS points
    LaunchedEffect(state.currentLat, state.currentLng) {
        if (state.currentLat != 0.0 && state.currentLng != 0.0) {
            val point = GeoPoint(state.currentLat, state.currentLng)
            routePoints.add(point)
            mapViewRef?.let { map ->
                // Clear and redraw polyline
                map.overlays.removeIf { it is Polyline }
                if (routePoints.size >= 2) {
                    val polyline = Polyline().apply {
                        setPoints(routePoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#00FF87")
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(polyline)
                }
                map.controller.setCenter(point)
                map.invalidate()
            }
        }
    }

    // Long-press progress animation
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            val steps = 50
            repeat(steps) { step ->
                longPressProgress = (step + 1f) / steps
                delay(40L) // 2 seconds total (50 × 40ms)
            }
            if (longPressProgress >= 1f) {
                // Stop tracking after confirmed long press
                viewModel.stopTracking(context)
                delay(1000L) // Give service time to save
                onTrackingFinished(-1L)
            }
        } else {
            longPressProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top stats panel
            TrackingStatsPanel(
                speedKmh = state.speedKmh,
                distanceMeters = state.distanceMeters,
                elapsedSeconds = state.elapsedSeconds,
                calories = state.calories,
                isPaused = state.isPaused,
                modifier = Modifier.weight(1f)
            )

            // Map view (smaller section at bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                OsmMapView(
                    context = context,
                    onMapReady = { mapViewRef = it }
                )

                // Auto-pause indicator overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = WarningAmber.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = DeepNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTO-PAUSED",
                                color = DeepNavy,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Long-press STOP button (floating, centered)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 260.dp)
        ) {
            StopButton(
                progress = longPressProgress,
                isLongPressing = isLongPressing,
                onLongPressStart = { isLongPressing = true },
                onLongPressEnd = { isLongPressing = false }
            )
        }
    }
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
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
            modifier = Modifier.size(22.dp)
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
private fun StopButton(
    progress: Float,
    isLongPressing: Boolean,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isLongPressing) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Progress ring around the stop button
        if (isLongPressing) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(84.dp),
                color = SpeedRed,
                strokeWidth = 4.dp,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SpeedRed, Color(0xFFAA0000))
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onLongPressStart()
                            try {
                                awaitRelease()
                            } finally {
                                onLongPressEnd()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Long press to stop",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "HOLD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OsmMapView(
    context: Context,
    onMapReady: (MapView) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                // My location overlay
                val myLocationOverlay = MyLocationNewOverlay(
                    GpsMyLocationProvider(ctx), this
                ).apply {
                    enableMyLocation()
                    enableFollowLocation()
                }
                overlays.add(myLocationOverlay)
                onMapReady(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { /* Map updates handled via LaunchedEffect above */ }
    )
}
