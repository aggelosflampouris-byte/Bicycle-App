package com.fitnessapp.tracker.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.service.OutdoorTrackingService
import com.fitnessapp.tracker.theme.DeepNavy
import com.fitnessapp.tracker.theme.LocalActivityTheme
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog
import com.fitnessapp.tracker.ui.dashboard.components.DashboardHeader
import com.fitnessapp.tracker.ui.dashboard.components.DigitalTwinDashboardCard
import com.fitnessapp.tracker.ui.dashboard.components.PreWorkoutCheckInDialog
import com.fitnessapp.tracker.ui.dashboard.components.RoutineConfigBottomSheet
import com.fitnessapp.tracker.ui.dashboard.components.RoutineProgressCard
import com.fitnessapp.tracker.ui.dashboard.components.SessionCard
import com.fitnessapp.tracker.ui.dashboard.components.StartWorkoutButton
import com.fitnessapp.tracker.ui.dashboard.components.StatsRow
import com.fitnessapp.tracker.ui.dashboard.components.TrophiesAndRecordsCard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun DashboardScreen(
    onStartWorkout: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTwin: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trackingState by OutdoorTrackingService.trackingState.collectAsStateWithLifecycle()
    val isTracking = trackingState.isTracking
    val context = LocalContext.current
    val activityType = LocalActivityTheme.current
    var showRoutineConfig by remember { mutableStateOf(false) }
    var showQuickCheckIn by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Long?>(null) }


    if (showQuickCheckIn) {
        PreWorkoutCheckInDialog(
            latestCheckIn = uiState.twinState?.latestCheckIn,
            onDismiss = { showQuickCheckIn = false },
            onSubmit = { sleep, energy, soreness, stress, hydration ->
                viewModel.onEvent(
                    DashboardUiEvent.SubmitWellnessCheckIn(
                        sleepQuality = sleep,
                        energyLevel = energy,
                        muscleSoreness = soreness,
                        stressLevel = stress,
                        hydrationScore = hydration
                    )
                )
                showQuickCheckIn = false
                Toast.makeText(context, "Digital Twin calibrated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (sessionToDelete != null) {
        DeleteConfirmationDialog(
            title = "Delete Activity",
            message = "Are you sure you want to delete this activity?",
            onConfirm = {
                viewModel.onEvent(DashboardUiEvent.DeleteSession(sessionToDelete!!))
                sessionToDelete = null
            },
            onDismiss = { sessionToDelete = null }
        )
    }

    LaunchedEffect(activityType) {
        viewModel.onEvent(DashboardUiEvent.SetActivityType(activityType))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {


        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                DashboardHeader(
                    userName = uiState.user.name,
                    activityType = activityType,
                    onSwitchActivity = {
                        if (isTracking) {
                            Toast.makeText(context, "Cannot switch activity while a workout is in progress.", Toast.LENGTH_SHORT).show()
                        } else {
                            val newActivity = when (ActivityType.fromString(activityType)) {
                                ActivityType.CYCLING -> ActivityType.WALKING.name
                                ActivityType.WALKING -> ActivityType.JOGGING.name
                                ActivityType.JOGGING -> ActivityType.CYCLING.name
                            }
                            viewModel.onEvent(DashboardUiEvent.SetActivityType(newActivity))
                        }
                    },
                    onSettingsClick = onOpenSettings
                )
            }

            // Digital Twin Readiness + Outdoor Microclimate Card
            item {
                if (!uiState.isLoading) {
                    DigitalTwinDashboardCard(
                        twinState = uiState.twinState,
                        isRefreshingWeather = uiState.isRefreshingEnvironment,
                        onRefreshWeather = { viewModel.onEvent(DashboardUiEvent.RefreshEnvironment) },
                        onQuickCheckInClick = { showQuickCheckIn = true },
                        onOpenTwinHub = onOpenTwin
                    )
                }
            }

            // Routine Progress Card
            item {
                if (!uiState.isLoading) {
                    RoutineProgressCard(
                        progress = uiState.routineProgress,
                        onConfigureClick = { showRoutineConfig = true }
                    )
                }
            }


            // Performance Benchmarks Card
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading && uiState.personalRecords.isNotEmpty(),
                    enter = fadeIn() + slideInVertically()
                ) {
                    TrophiesAndRecordsCard(
                        records = uiState.personalRecords,
                        activityType = activityType
                    )
                }
            }


            // Stats cards
            item {
                if (!uiState.isLoading) {
                    StatsRow(uiState = uiState, activityType = activityType)
                }
                Spacer(modifier = Modifier.height(24.dp))
                StartWorkoutButton(
                    onClick = onStartWorkout,
                    activityType = activityType
                )
            }

            // Recent sessions header
            item {
                if (uiState.sessions.isNotEmpty()) {
                    val recentTitle = "Recent ${ActivityType.fromString(activityType).nounPlural.replaceFirstChar { it.uppercase() }}"
                    Text(
                        text = recentTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Session list
            items(
                items = uiState.sessions,
                key = { it.id }
            ) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionClick(session.id) },
                    onDelete = { sessionToDelete = session.id }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showRoutineConfig) {
            RoutineConfigBottomSheet(
                currentProgress = uiState.routineProgress,
                onDismiss = { showRoutineConfig = false },
                onSave = { interval, metric, target, autoImprove ->
                    viewModel.onEvent(DashboardUiEvent.SaveRoutine(interval, metric, target, autoImprove))
                    showRoutineConfig = false
                },
                onDelete = {
                    viewModel.onEvent(DashboardUiEvent.DeleteRoutine)
                    showRoutineConfig = false
                }
            )
        }
    }
}

