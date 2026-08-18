package com.fitnessapp.tracker.ui.dashboard

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
    var showSegmentsScreen by remember { mutableStateOf(false) }

    if (showSegmentsScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSegmentsScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.fitnessapp.tracker.ui.segments.SegmentsScreen(
                onBack = { showSegmentsScreen = false }
            )
        }
    }

    if (sessionToDelete != null) {
        com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
            title = "Delete Activity",
            message = "Are you sure you want to delete this activity?",
            onConfirm = {
                viewModel.deleteSession(sessionToDelete!!)
                sessionToDelete = null
            },
            onDismiss = { sessionToDelete = null }
        )
    }

    LaunchedEffect(activityType) {
        viewModel.setActivityType(activityType)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        val primaryColor = ElectricGreen
        
        // Decorative gradient orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.8f, size.height * 0.1f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VividCyan.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(0f, size.height * 0.6f),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(0f, size.height * 0.6f)
            )
        }

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
                            viewModel.setActivityType(newActivity)
                        }
                    },
                    onSettingsClick = onOpenSettings
                )
            }

            // Routine Progress Card
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading,
                    enter = fadeIn() + slideInVertically()
                ) {
                    RoutineProgressCard(
                        progress = uiState.routineProgress,
                        onConfigureClick = { showRoutineConfig = true }
                    )
                }
            }

            // Daily Challenge Card
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading,
                    enter = fadeIn() + slideInVertically()
                ) {
                    val challenge = uiState.latestChallenge
                    if (challenge != null && (challenge.status == ChallengeStatus.PENDING || challenge.status == ChallengeStatus.ACCEPTED || challenge.status == ChallengeStatus.ACTIVE)) {
                        ChallengeCard(
                            challenge = challenge,
                            onAccept = { viewModel.respondToChallenge(challenge, true) },
                            onDeny = { viewModel.respondToChallenge(challenge, false) },
                            onCancel = { viewModel.cancelChallenge(challenge) }
                        )
                    }
                }
            }


            // Weekly Training Plan Card
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading,
                    enter = fadeIn() + slideInVertically()
                ) {
                    var showGoalDialog by remember { mutableStateOf(false) }

                    if (showGoalDialog) {
                        TrainingPlanGoalDialog(
                            onDismiss = { showGoalDialog = false },
                            onSelectGoal = { goal ->
                                showGoalDialog = false
                                viewModel.generateTrainingPlan(goal)
                            }
                        )
                    }

                    WeeklyTrainingPlanCard(
                        trainingPlan = uiState.trainingPlan,
                        isGenerating = uiState.isGeneratingPlan,
                        onGeneratePlan = { showGoalDialog = true },
                        onTogglePlanCompleted = { day -> viewModel.toggleDailyPlanCompleted(day) }
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

            // Recovery Readiness Card
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading && uiState.recoveryAdvice != null,
                    enter = fadeIn() + slideInVertically()
                ) {
                    uiState.recoveryAdvice?.let { recovery ->
                        RecoveryReadinessCard(recovery = recovery)
                    }
                }
            }

            // Road Segments & Ghost Pacing Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSegmentsScreen = true },
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, VividCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(VividCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🏁", fontSize = 20.sp)
                            }
                            Column {
                                Text("Road Segments & Ghost Pacer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Local sprint leaderboards & virtual pacer", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = VividCyan)
                    }
                }
            }

            // Stats cards
            item {
                AnimatedVisibility(
                    visible = !uiState.isLoading,
                    enter = fadeIn() + slideInVertically()
                ) {
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
                    viewModel.saveRoutine(interval, metric, target, autoImprove)
                    showRoutineConfig = false
                },
                onDelete = {
                    viewModel.deleteRoutine()
                    showRoutineConfig = false
                }
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    userName: String,
    activityType: String,
    onSwitchActivity: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hello, $userName 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitleText = when (activityType) {
                "WALKING" -> "Ready to walk?"
                "JOGGING" -> "Ready to jog?"
                else -> "Ready to ride?"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onSwitchActivity,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NavyCard)
            ) {
                Crossfade(targetState = activityType, label = "activityIcon") { type ->
                    val icon = when (type) {
                        "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                        "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
                        else -> Icons.AutoMirrored.Filled.DirectionsBike
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Switch Activity",
                        tint = TextSecondary
                    )
                }
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NavyCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatsRow(uiState: DashboardUiState, activityType: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Total Distance",
            value = "${"%.1f".format(uiState.totalDistanceKm)}",
            unit = "km",
            icon = Icons.Default.Route,
            iconTint = ElectricGreen
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Avg Distance",
            value = "${"%.1f".format(uiState.avgDistanceKm)}",
            unit = "km",
            icon = Icons.Default.ShowChart,
            iconTint = VividCyan
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = when (activityType) {
                "WALKING" -> "Total Walks"
                "JOGGING" -> "Total Jogs"
                else -> "Total Rides"
            },
            value = "${uiState.totalSessions}",
            unit = when (activityType) {
                "WALKING" -> "walks"
                "JOGGING" -> "jogs"
                else -> "rides"
            },
            icon = when (activityType) {
                "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
                else -> Icons.AutoMirrored.Filled.DirectionsBike
            },
            iconTint = WarningAmber
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Calories Burned",
            value = "${"%.0f".format(uiState.totalCalories)}",
            unit = "kcal",
            icon = Icons.Default.LocalFireDepartment,
            iconTint = SpeedRed
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StartWorkoutButton(activityType: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricGreen,
            contentColor = DeepNavy
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp
        )
    ) {
        val icon = when (activityType) {
            "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
            "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
            else -> Icons.AutoMirrored.Filled.DirectionsBike
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        val actionText = when (activityType) {
            "WALKING" -> "START WALKING"
            "JOGGING" -> "START JOGGING"
            else -> "START CYCLING"
        }
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(session.startTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isGold = session.isChallengeCompletion
            val bubbleColor = if (isGold) androidx.compose.ui.graphics.Color(0xFFFFD700) else ElectricGreen
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(bubbleColor.copy(alpha = 0.3f), NavyLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val sessionIcon = when (session.activityType) {
                    "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                    "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
                    else -> Icons.AutoMirrored.Filled.DirectionsBike
                }
                Icon(
                    imageVector = sessionIcon,
                    contentDescription = null,
                    tint = bubbleColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PhysicsEngine.formatDistance(session.totalDistanceMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$date  •  ${PhysicsEngine.formatDuration(session.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat(label = "Avg", value = "${"%.1f".format(session.avgSpeedKmh)} km/h")
                    MiniStat(label = "Cal", value = "${"%.0f".format(session.caloriesBurned)} kcal")
                    MiniStat(label = "Elev", value = "${"%.0f".format(session.elevationGainMeters)}m")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SpeedRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = ElectricGreen)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineConfigBottomSheet(
    currentProgress: com.fitnessapp.tracker.data.local.RoutineProgress?,
    onDismiss: () -> Unit,
    onSave: (interval: RoutineInterval, metric: RoutineMetric, target: Double, autoImprove: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var interval by remember { mutableStateOf(currentProgress?.routine?.interval ?: RoutineInterval.WEEKLY) }
    var metric by remember { mutableStateOf(currentProgress?.routine?.metric ?: RoutineMetric.DISTANCE) }
    var target by remember { mutableStateOf(currentProgress?.routine?.targetValue?.toString() ?: "50.0") }
    var autoImprove by remember { mutableStateOf(currentProgress?.routine?.autoImprove ?: true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
            title = "Clear Goal",
            message = "Are you sure you want to clear your workout goal?",
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepNavy,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Workout Goal", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)

            // Interval selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineInterval.entries.forEach { opt ->
                    FilterChip(
                        selected = interval == opt,
                        onClick = { interval = opt },
                        label = { Text(opt.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricGreen,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            // Metric selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineMetric.entries.forEach { opt ->
                    FilterChip(
                        selected = metric == opt,
                        onClick = { metric = opt },
                        label = { Text(if (opt == RoutineMetric.DISTANCE) "Distance (km)" else "Calories") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricGreen,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            // Target Input
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target Goal", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricGreen,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard
                )
            )

            // Auto-improve toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto-Improve Goal", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Increases goal by 5% when completed", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = autoImprove,
                    onCheckedChange = { autoImprove = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepNavy,
                        checkedTrackColor = ElectricGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (currentProgress != null) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpeedRed),
                        border = BorderStroke(1.dp, SpeedRed)
                    ) {
                        Text("Clear Goal")
                    }
                }
                Button(
                    onClick = {
                        val t = target.toDoubleOrNull() ?: 0.0
                        if (t > 0) onSave(interval, metric, t, autoImprove)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RoutineProgressCard(
    progress: com.fitnessapp.tracker.data.local.RoutineProgress?,
    onConfigureClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onConfigureClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TrackChanges, null, tint = ElectricGreen, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (progress == null) "Set a Workout Goal" else "${progress.routine.interval.name.lowercase().replaceFirstChar { it.uppercase() }} Goal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            
            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val percentage = (progress.currentValue / progress.routine.targetValue).coerceIn(0.0, 1.0)
                val unit = if (progress.routine.metric == RoutineMetric.DISTANCE) "km" else "kcal"
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${"%.1f".format(progress.currentValue)} / ${"%.1f".format(progress.routine.targetValue)} $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { percentage.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = ElectricGreen,
                    trackColor = NavyLight
                )
                if (progress.isCompleted && progress.routine.autoImprove) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Goal completed! Your next interval goal will increase by ${(progress.routine.autoImprovePercentage * 100).toInt()}%.", style = MaterialTheme.typography.bodySmall, color = VividCyan)
                } else if (progress.isCompleted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Goal completed! Excellent work.", style = MaterialTheme.typography.bodySmall, color = ElectricGreen)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Track progress automatically and get smart reminders.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ChallengeCard(
    challenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity,
    onAccept: () -> Unit,
    onDeny: () -> Unit,
    onCancel: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
            title = "Cancel Challenge",
            message = "Are you sure you want to cancel this active challenge? You will be able to switch activities or start a new challenge afterwards.",
            onConfirm = {
                onCancel()
                showCancelDialog = false
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFD700))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${challenge.period.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Challenge",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val targetStr = when(challenge.metric) {
                ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.targetValue)
                ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.targetValue)} km/h"
                ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.targetValue)} kcal"
            }
            val progressStr = when(challenge.metric) {
                ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.currentProgress)
                ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.currentProgress)} km/h"
                ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.currentProgress)} kcal"
            }
            Text(
                text = "${challenge.activityType.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}: $targetStr",
                style = MaterialTheme.typography.bodyLarge,
                color = ElectricGreen
            )
            
            if (challenge.status == ChallengeStatus.PENDING) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDeny) {
                        Text("Deny", color = SpeedRed)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy)
                    ) {
                        Text("Accept", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (challenge.status == ChallengeStatus.ACTIVE || challenge.status == ChallengeStatus.ACCEPTED) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (challenge.currentProgress / challenge.targetValue).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = androidx.compose.ui.graphics.Color(0xFFFFD700),
                    trackColor = NavyDarker
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress: $progressStr / $targetStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    TextButton(
                        onClick = { showCancelDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = SpeedRed)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel Challenge", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Weekly Training Plan UI ───────────────────────────────────────────────────

@Composable
fun WeeklyTrainingPlanCard(
    trainingPlan: com.fitnessapp.tracker.data.local.entity.TrainingPlanEntity?,
    isGenerating: Boolean,
    onGeneratePlan: () -> Unit,
    onTogglePlanCompleted: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = trainingPlan != null) { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(VividCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = VividCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Training Plan",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (trainingPlan != null) {
                        Text(
                            text = if (expanded) "Tap to collapse" else "Tap to view full week",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        Text(
                            text = "No active plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (trainingPlan == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGeneratePlan,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VividCyan)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepNavy)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...", color = DeepNavy)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate 7-Day Plan", color = DeepNavy, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val plans = remember(trainingPlan.planJson) {
                    try {
                        val type = object : TypeToken<List<DailyPlan>>() {}.type
                        Gson().fromJson<List<DailyPlan>>(trainingPlan.planJson, type)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        plans.forEach { plan ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { onTogglePlanCompleted(plan.day) }
                                    .background(if (plan.isCompleted) ElectricGreen.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = plan.isCompleted,
                                    onCheckedChange = { onTogglePlanCompleted(plan.day) },
                                    colors = CheckboxDefaults.colors(checkedColor = ElectricGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${plan.day.take(3).uppercase()} - ${plan.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (plan.isCompleted) ElectricGreen else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (plan.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        text = plan.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onGeneratePlan,
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, VividCyan)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = VividCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Regenerate with New Goal", color = VividCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingPlanGoalDialog(
    onDismiss: () -> Unit,
    onSelectGoal: (String) -> Unit
) {
    val goals = listOf(
        "Balanced Endurance Building",
        "Speed & HIIT Interval Power",
        "Recovery & Low-Heart-Rate Base",
        "Gran Fondo / 100km Century Prep",
        "Fat Loss & Calorie Burn Focus"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Training Goal",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Qwen will generate an adaptive 7-day schedule based on your goal and recent workouts:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                goals.forEach { goal ->
                    Button(
                        onClick = { onSelectGoal(goal) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDarker,
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Text(
                            text = goal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = NavyCard,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun TrophiesAndRecordsCard(
    records: List<com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity>,
    activityType: String
) {
    if (records.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "All-Time Records & Trophies",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${records.size}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            val displayRecords = if (isExpanded) records else records.take(3)

            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayRecords.forEach { record ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DeepNavy.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(record.recordType.icon, fontSize = 16.sp)
                                Text(
                                    text = record.recordType.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            val formattedVal = when (record.recordType) {
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.FASTEST_1KM,
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.FASTEST_5KM,
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.FASTEST_10KM,
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.FASTEST_20KM,
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.FASTEST_50KM -> {
                                    val sec = record.value.toLong()
                                    val m = (sec % 3600) / 60
                                    val s = sec % 60
                                    val h = sec / 3600
                                    if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%dm %02ds".format(m, s)
                                }
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.LONGEST_DURATION -> {
                                    val sec = record.value.toLong()
                                    val m = (sec % 3600) / 60
                                    val s = sec % 60
                                    val h = sec / 3600
                                    if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%dm %02ds".format(m, s)
                                }
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.LONGEST_DISTANCE -> "%.2f km".format(record.value / 1000.0)
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.MAX_ELEVATION_GAIN -> "+%.0f m".format(record.value)
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.MAX_AVG_SPEED -> "%.1f km/h".format(record.value)
                                com.fitnessapp.tracker.data.local.entity.PersonalRecordType.MAX_CALORIES -> "%.0f kcal".format(record.value)
                            }
                            Text(
                                text = formattedVal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryReadinessCard(
    recovery: com.fitnessapp.tracker.engine.RecoveryAdvice
) {
    var isExpanded by remember { mutableStateOf(false) }
    val statusColor = Color(android.graphics.Color.parseColor(recovery.status.colorHex))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Recovery Readiness",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = recovery.status.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${recovery.recoveryPercentage}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { recovery.recoveryPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = NavyDarker
            )

            // Quick summary line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (recovery.remainingRecoveryHours > 0) "${recovery.remainingRecoveryHours}h until full recovery" else "Fully restored",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Sleep target: %.1fh".format(recovery.recommendedSleepHours),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = GlassBorder)
                    Text(
                        text = recovery.status.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    recovery.actionableTips.take(2).forEach { tip ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = statusColor, fontWeight = FontWeight.Bold)
                            Text(text = tip, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}


