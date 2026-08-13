package com.fitnessapp.tracker.ui.history

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.dashboard.DashboardViewModel
import com.fitnessapp.tracker.ui.progress.ProgressScreen
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryFilter { ALL, LONG_RIDES, FAST_RIDES }
enum class HistoryView { LIST, PROGRESS }

@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val activityType = LocalActivityTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedView by remember { mutableStateOf(HistoryView.LIST) }
    var sessionToDelete by remember { mutableStateOf<Long?>(null) }

    val filteredSessions = remember(uiState.sessions, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> uiState.sessions
            HistoryFilter.LONG_RIDES -> {
                when (activityType) {
                    "WALKING" -> uiState.sessions.filter { it.totalDistanceMeters >= 5000.0 }
                    "JOGGING" -> uiState.sessions.filter { it.totalDistanceMeters >= 5000.0 }
                    else -> uiState.sessions.filter { it.totalDistanceMeters >= 10000.0 }
                }
            }
            HistoryFilter.FAST_RIDES -> {
                when (activityType) {
                    "WALKING" -> uiState.sessions.filter { it.avgSpeedKmh >= 6.0 }
                    "JOGGING" -> uiState.sessions.filter { it.avgSpeedKmh >= 8.0 }
                    else -> uiState.sessions.filter { it.avgSpeedKmh >= 20.0 }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // Toggle view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NavyCard)
                .padding(4.dp)
        ) {
            val listSelected = selectedView == HistoryView.LIST
            val progressSelected = selectedView == HistoryView.PROGRESS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (listSelected) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { selectedView = HistoryView.LIST }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (activityType) {
                        "WALKING" -> "Walks"
                        "JOGGING" -> "Jogs"
                        else -> "Rides"
                    },
                    color = if (listSelected) ElectricGreen else TextSecondary,
                    fontWeight = if (listSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (progressSelected) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { selectedView = HistoryView.PROGRESS }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Analytics",
                    color = if (progressSelected) ElectricGreen else TextSecondary,
                    fontWeight = if (progressSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }

        if (selectedView == HistoryView.LIST) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Summary Totals Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${"%.1f".format(uiState.totalDistanceKm)} km",
                                style = MaterialTheme.typography.titleLarge,
                                color = ElectricGreen,
                                fontWeight = FontWeight.Black
                            )
                            Text("Total Dist", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${uiState.totalSessions}",
                                style = MaterialTheme.typography.titleLarge,
                                color = VividCyan,
                                fontWeight = FontWeight.Black
                            )
                            Text(when (activityType) {
                                "WALKING" -> "Walks"
                                "JOGGING" -> "Jogs"
                                else -> "Rides"
                            }, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${"%.0f".format(uiState.totalCalories)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = SpeedRed,
                                fontWeight = FontWeight.Black
                            )
                            Text("Calories", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.ALL,
                        onClick = { selectedFilter = HistoryFilter.ALL },
                        label = { Text("All (${uiState.sessions.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricGreen
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.LONG_RIDES,
                        onClick = { selectedFilter = HistoryFilter.LONG_RIDES },
                        label = { Text(when (activityType) {
                            "WALKING" -> "Long (>5km)"
                            "JOGGING" -> "Long (>5km)"
                            else -> "Long (>10km)"
                        }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VividCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VividCyan
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.FAST_RIDES,
                        onClick = { selectedFilter = HistoryFilter.FAST_RIDES },
                        label = { Text(when (activityType) {
                            "WALKING" -> "Fast (>6km/h)"
                            "JOGGING" -> "Fast (>8km/h)"
                            else -> "Fast (>20km/h)"
                        }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarningAmber.copy(alpha = 0.2f),
                            selectedLabelColor = WarningAmber
                        )
                    )
                }
            }

            // Empty State or List
            if (filteredSessions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ElectricGreen.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (activityType) {
                                "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
                                else -> Icons.AutoMirrored.Filled.DirectionsBike
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = if (uiState.sessions.isEmpty()) {
                                when (activityType) {
                                    "WALKING" -> "No recorded walks yet"
                                    "JOGGING" -> "No recorded jogs yet"
                                    else -> "No recorded rides yet"
                                }
                            } else {
                                when (activityType) {
                                    "WALKING" -> "No walks match this filter"
                                    "JOGGING" -> "No jogs match this filter"
                                    else -> "No rides match this filter"
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (activityType) {
                                "WALKING" -> "Record your walking sessions to see detailed stats here"
                                "JOGGING" -> "Record your jogging sessions to see detailed stats here"
                                else -> "Record your cycling sessions to see detailed stats here"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStartWorkout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricGreen,
                                contentColor = DeepNavy
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            val buttonText = when (activityType) {
                                "WALKING" -> "Start a Walk"
                                "JOGGING" -> "Start a Jog"
                                else -> "Start a Ride"
                            }
                            Text(buttonText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(
                    items = filteredSessions,
                    key = { it.id }
                ) { session ->
                    HistoryItemCard(
                        session = session,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { sessionToDelete = session.id }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
        } else {
            ProgressScreen()
        }
    }
}

@Composable
private fun HistoryItemCard(
    session: WorkoutSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(if (session.isChallengeCompletion) 2.dp else 1.dp, if (session.isChallengeCompletion) androidx.compose.ui.graphics.Color(0xFFFFD700) else GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    if (session.isChallengeCompletion) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Challenge Completed",
                            tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = SpeedRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = PhysicsEngine.formatDistance(session.totalDistanceMeters),
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Dist", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${"%.0f".format(session.elevationGainMeters)}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Elev", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = PhysicsEngine.formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = PhysicsEngine.formatSpeed(session.avgSpeedKmh),
                        style = MaterialTheme.typography.titleMedium,
                        color = VividCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text("km/h", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${"%.0f".format(session.caloriesBurned)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SpeedRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Cals", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}
