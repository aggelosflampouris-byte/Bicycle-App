package com.fitnessapp.tracker.ui.dashboard

import com.fitnessapp.tracker.ui.dashboard.components.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.*
import java.text.SimpleDateFormat
import java.util.*
import com.fitnessapp.tracker.data.local.entity.DailyPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.fitnessapp.tracker.service.CyclingTrackingService
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.data.local.entity.ChallengeMetric
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric
import android.widget.Toast

@Composable
fun DashboardScreen(
    onStartWorkout: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trackingState by CyclingTrackingService.trackingState.collectAsStateWithLifecycle()
    val isTracking = trackingState.isTracking
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityType = LocalActivityTheme.current
    var showRoutineConfig by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Long?>(null) }


    if (sessionToDelete != null) {
        com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
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
                        val activeChallenge = uiState.latestChallenge
                        val isChallengeActive = activeChallenge != null &&
                            (activeChallenge.status == ChallengeStatus.ACCEPTED || activeChallenge.status == ChallengeStatus.ACTIVE)

                        if (isTracking) {
                            Toast.makeText(context, "Cannot switch activity while a workout is in progress.", Toast.LENGTH_SHORT).show()
                        } else if (isChallengeActive && activeChallenge != null) {
                            Toast.makeText(
                                context,
                                "A ${activeChallenge.activityType.lowercase()} challenge is currently active. You must complete it through a workout or cancel it before switching activities.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val newActivity = when (activityType) {
                                "CYCLING" -> "WALKING"
                                "WALKING" -> "JOGGING"
                                else -> "CYCLING"
                            }
                            viewModel.onEvent(DashboardUiEvent.SetActivityType(newActivity))
                        }
                    },
                    onSettingsClick = onOpenSettings
                )
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

            // Daily Challenge Card
            item {
                if (!uiState.isLoading) {
                    val challenge = uiState.latestChallenge
                    if (challenge != null && (challenge.status == ChallengeStatus.PENDING || challenge.status == ChallengeStatus.ACCEPTED || challenge.status == ChallengeStatus.ACTIVE)) {
                        ChallengeCard(
                            challenge = challenge,
                            onAccept = { viewModel.onEvent(DashboardUiEvent.RespondToChallenge(challenge, true)) },
                            onDeny = { viewModel.onEvent(DashboardUiEvent.RespondToChallenge(challenge, false)) },
                            onCancel = { viewModel.onEvent(DashboardUiEvent.CancelChallenge(challenge)) }
                        )
                    }
                }
            }


            // Weekly Training Plan Card
            item {
                if (!uiState.isLoading) {
                    var showGoalDialog by remember { mutableStateOf(false) }

                    if (showGoalDialog) {
                        TrainingPlanGoalDialog(
                            onDismiss = { showGoalDialog = false },
                            onSelectGoal = { goal ->
                                showGoalDialog = false
                                viewModel.onEvent(DashboardUiEvent.GenerateTrainingPlan(goal))
                            }
                        )
                    }

                    WeeklyTrainingPlanCard(
                        trainingPlan = uiState.trainingPlan,
                        isGenerating = uiState.isGeneratingPlan,
                        onGeneratePlan = { showGoalDialog = true },
                        onTogglePlanCompleted = { day -> viewModel.onEvent(DashboardUiEvent.ToggleDailyPlanCompleted(day)) }
                    )
                }
            }

            // All-Time Records & Trophies Card
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
                    Text(
                        text = when (activityType) {
                            "WALKING" -> "Recent Walks"
                            "JOGGING" -> "Recent Jogs"
                            else -> "Recent Rides"
                        },
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

