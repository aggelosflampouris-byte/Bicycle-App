package com.example.smartcyclingtracker.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.engine.PhysicsEngine
import com.example.smartcyclingtracker.service.CyclingTrackingService
import com.example.smartcyclingtracker.theme.*
import com.example.smartcyclingtracker.ui.chat.AiChatScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardViewModel
import com.example.smartcyclingtracker.ui.history.HistoryScreen
import com.example.smartcyclingtracker.ui.settings.SettingsScreen
import com.example.smartcyclingtracker.updater.UpdateDialog
import com.example.smartcyclingtracker.updater.UpdaterViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.AutoMirrored.Filled.DirectionsBike),
    AI_COACH("AI Coach", Icons.Default.Psychology),
    CHALLENGES("Challenges", Icons.Default.EmojiEvents),
    HISTORY("Activities", Icons.Default.Assessment),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    onStartWorkout: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    updaterViewModel: UpdaterViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val activityType = LocalActivityTheme.current
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs = remember { MainTab.entries.toTypedArray() }
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }

    val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    val isTracking by remember {
        CyclingTrackingService.trackingState.map { it.isTracking }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val updateState by updaterViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fluid Back Handling: if not on Home tab, back button returns to Home smoothly
    BackHandler(enabled = currentTab != MainTab.DASHBOARD) {
        currentTab = MainTab.DASHBOARD
    }

    // Show feedback snackbar for update check results
    LaunchedEffect(updateState.userMessage) {
        updateState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            updaterViewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = DeepNavy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Floating Active Ride Mini-Player Bar (Strava-style)
                AnimatedVisibility(
                    visible = isTracking,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    ActiveRideMiniBanner(
                        onClick = onStartWorkout
                    )
                }

                NavigationBar(
                    containerColor = NavyDarker,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    tabs.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = tab
                            },
                            icon = {
                                val displayIcon = if (tab == MainTab.DASHBOARD) {
                                    when (activityType) {
                                        "WALKING" -> Icons.Default.DirectionsWalk
                                        "JOGGING" -> Icons.Default.DirectionsRun
                                        else -> Icons.Default.DirectionsBike
                                    }
                                } else {
                                    tab.icon
                                }
                                Icon(
                                    imageVector = displayIcon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) ElectricGreen else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSelected) ElectricGreen else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricGreen,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = ElectricGreen,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = ElectricGreen.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val saveableStateHolder = rememberSaveableStateHolder()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + 
                     slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { fullHeight -> fullHeight / 12 }
                     )).togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "tab_transition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                saveableStateHolder.SaveableStateProvider(targetTab.name) {
                    when (targetTab) {
                        MainTab.DASHBOARD -> {
                            DashboardScreen(
                                onStartWorkout = onStartWorkout,
                                onSessionClick = onSessionClick,
                                onOpenSettings = {
                                    currentTab = MainTab.SETTINGS
                                }
                            )
                        }
                    MainTab.AI_COACH -> {
                        AiChatScreen(
                            onBack = {
                                currentTab = MainTab.DASHBOARD
                            }
                        )
                    }
                    MainTab.HISTORY -> {
                        HistoryScreen(
                            onSessionClick = onSessionClick,
                            onStartWorkout = onStartWorkout
                        )
                    }
                    MainTab.CHALLENGES -> {
                        com.example.smartcyclingtracker.ui.challenges.ChallengesScreen()
                    }
                    MainTab.SETTINGS -> {
                        SettingsScreen()
                    }
                }
                } // End of SaveableStateProvider
            }

            // In-App Update Dialog
            if (updateState.showDialog && updateState.updateInfo != null) {
                UpdateDialog(
                    updateInfo = updateState.updateInfo!!,
                    isDownloading = updateState.isDownloading,
                    downloadProgress = updateState.downloadProgress,
                    onConfirmUpdate = {
                        updaterViewModel.startDownloadAndInstall(context)
                    },
                    onDismiss = { updaterViewModel.dismissDialog() }
                )
            }

            // In-App New Challenge Dialog
            val pendingChallenge = dashboardUiState.latestChallenge
            if (dashboardUiState.showNewChallengeDialog && pendingChallenge != null) {
                AlertDialog(
                    onDismissRequest = { dashboardViewModel.dismissNewChallengeDialog() },
                    containerColor = NavyCard,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🏅 New Challenge!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        val metricLabel = when (pendingChallenge.metric) {
                            "DISTANCE" -> PhysicsEngine.formatDistance(pendingChallenge.targetValue)
                            "SPEED"    -> "${PhysicsEngine.formatSpeed(pendingChallenge.targetValue)} km/h"
                            "CALORIES" -> "${"%.0f".format(pendingChallenge.targetValue)} kcal"
                            else       -> pendingChallenge.targetValue.toString()
                        }
                        Text(
                            "A new ${pendingChallenge.period.lowercase()} " +
                            "${pendingChallenge.activityType.lowercase()} challenge has arrived!\n\n" +
                            "Goal: $metricLabel",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                dashboardViewModel.respondToChallenge(pendingChallenge, true)
                                dashboardViewModel.dismissNewChallengeDialog()
                                currentTab = MainTab.CHALLENGES
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricGreen,
                                contentColor = DeepNavy
                            )
                        ) {
                            Text("Accept", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                dashboardViewModel.respondToChallenge(pendingChallenge, false)
                                dashboardViewModel.dismissNewChallengeDialog()
                            }
                        ) {
                            Text("Deny", color = SpeedRed)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveRideMiniBanner(
    onClick: () -> Unit
) {
    val trackingState by CyclingTrackingService.trackingState.collectAsStateWithLifecycle()
    val activityType = LocalActivityTheme.current
    
    val speedKmh = trackingState.speedKmh
    val distanceMeters = trackingState.distanceMeters
    val elapsedSeconds = trackingState.elapsedSeconds
    val isPaused = trackingState.isPaused
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.5.dp, if (isPaused) WarningAmber else ElectricGreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .graphicsLayer { alpha = if (isPaused) 1f else pulseAlpha }
                        .background(
                            if (isPaused) WarningAmber else ElectricGreen
                        )
                )
                Column {
                    Text(
                        text = if (isPaused) "RIDE PAUSED" else "RIDE IN PROGRESS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPaused) WarningAmber else ElectricGreen
                    )
                    Text(
                        text = "${PhysicsEngine.formatSpeed(speedKmh)} km/h • ${PhysicsEngine.formatDistance(distanceMeters)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = PhysicsEngine.formatDuration(elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                val icon = when (activityType) {
                    "WALKING" -> Icons.Default.DirectionsWalk
                    "JOGGING" -> Icons.Default.DirectionsRun
                    else -> Icons.Default.DirectionsBike
                }
                Icon(icon, contentDescription = "Active Workout", tint = ElectricGreen)
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
