package com.example.smartcyclingtracker.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.engine.PhysicsEngine
import com.example.smartcyclingtracker.service.CyclingTrackingService
import com.example.smartcyclingtracker.theme.*
import com.example.smartcyclingtracker.ui.chat.AiChatScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardScreen
import com.example.smartcyclingtracker.ui.history.HistoryScreen
import com.example.smartcyclingtracker.ui.onboarding.OnboardingScreen
import com.example.smartcyclingtracker.updater.UpdateDialog
import com.example.smartcyclingtracker.updater.UpdaterViewModel
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.AutoMirrored.Filled.DirectionsBike),
    AI_COACH("VeloCoach", Icons.Default.Psychology),
    HISTORY("Activities", Icons.Default.Assessment),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun MainScreen(
    onStartWorkout: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    updaterViewModel: UpdaterViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tabs = remember { MainTab.values() }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    val trackingState by CyclingTrackingService.trackingState.collectAsStateWithLifecycle()
    val updateState by updaterViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fluid Back Handling: if not on Home tab, back button returns to Home smoothly
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    // Show feedback snackbar for update check results
    LaunchedEffect(updateState.userMessage) {
        updateState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            updaterViewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = DeepNavy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Floating Active Ride Mini-Player Bar (Strava/Spotify style)
                AnimatedVisibility(
                    visible = trackingState.isTracking,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    ActiveRideMiniBanner(
                        speedKmh = trackingState.speedKmh,
                        distanceMeters = trackingState.distanceMeters,
                        elapsedSeconds = trackingState.elapsedSeconds,
                        isPaused = trackingState.isPaused,
                        onClick = onStartWorkout
                    )
                }

                NavigationBar(
                    containerColor = NavyDarker,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = pagerState.currentPage == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Swipeable Fluid Pager for smooth gesture navigation between tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (tabs[page]) {
                    MainTab.DASHBOARD -> {
                        DashboardScreen(
                            onStartWorkout = onStartWorkout,
                            onSessionClick = onSessionClick,
                            onOpenSettings = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        )
                    }
                    MainTab.AI_COACH -> {
                        AiChatScreen(
                            onBack = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                    }
                    MainTab.HISTORY -> {
                        HistoryScreen(
                            onSessionClick = onSessionClick,
                            onStartWorkout = onStartWorkout
                        )
                    }
                    MainTab.PROFILE -> {
                        OnboardingScreen(
                            onComplete = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                    }
                }
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
                    onDismiss = {
                        updaterViewModel.dismissDialog()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveRideMiniBanner(
    speedKmh: Double,
    distanceMeters: Double,
    elapsedSeconds: Long,
    isPaused: Boolean,
    onClick: () -> Unit
) {
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
                        .background(
                            if (isPaused) WarningAmber else ElectricGreen.copy(alpha = pulseAlpha)
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
