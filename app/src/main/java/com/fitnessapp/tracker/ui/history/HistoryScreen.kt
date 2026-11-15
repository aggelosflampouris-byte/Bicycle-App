package com.fitnessapp.tracker.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.theme.DeepNavy
import com.fitnessapp.tracker.theme.ElectricGreen
import com.fitnessapp.tracker.theme.GlassBorder
import com.fitnessapp.tracker.theme.LocalActivityTheme
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.SpeedRed
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
import com.fitnessapp.tracker.theme.VividCyan
import com.fitnessapp.tracker.theme.WarningAmber
import com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog
import com.fitnessapp.tracker.ui.dashboard.DashboardUiEvent
import com.fitnessapp.tracker.ui.dashboard.DashboardViewModel
import com.fitnessapp.tracker.ui.dashboard.components.SessionCard
import com.fitnessapp.tracker.ui.progress.ProgressScreen
import com.fitnessapp.tracker.ui.util.icon

enum class HistoryFilter { ALL, LONG_SESSIONS, FAST_PACE }
enum class HistoryView { LIST, PROGRESS }

@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val activityType = LocalActivityTheme.current
    val activity = ActivityType.fromString(activityType)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedView by remember { mutableStateOf(HistoryView.LIST) }
    var sessionToDelete by remember { mutableStateOf<Long?>(null) }

    val filteredSessions = remember(uiState.sessions, selectedFilter, activity) {
        when (selectedFilter) {
            HistoryFilter.ALL -> uiState.sessions
            HistoryFilter.LONG_SESSIONS -> {
                uiState.sessions.filter { it.totalDistanceMeters >= activity.longSessionThresholdMeters }
            }
            HistoryFilter.FAST_PACE -> {
                uiState.sessions.filter { it.avgSpeedKmh >= activity.fastSpeedThresholdKmh }
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
                    text = activity.nounPlural.replaceFirstChar { it.uppercase() },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Summary Totals Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
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
                                fontWeight = FontWeight.Bold
                            )
                            Text("Total Dist", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${uiState.totalSessions}",
                                style = MaterialTheme.typography.titleLarge,
                                color = VividCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(activity.nounPlural.replaceFirstChar { it.uppercase() }, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${"%.0f".format(uiState.totalCalories)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = SpeedRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Calories", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Filter Chips
            item {
                val longKm = (activity.longSessionThresholdMeters / 1000.0).toInt()
                val fastThreshold = if (activity.fastSpeedThresholdKmh % 1.0 == 0.0) {
                    activity.fastSpeedThresholdKmh.toInt().toString()
                } else {
                    activity.fastSpeedThresholdKmh.toString()
                }
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
                        selected = selectedFilter == HistoryFilter.LONG_SESSIONS,
                        onClick = { selectedFilter = HistoryFilter.LONG_SESSIONS },
                        label = { Text("Long (>${longKm}km)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VividCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VividCyan
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.FAST_PACE,
                        onClick = { selectedFilter = HistoryFilter.FAST_PACE },
                        label = { Text("Fast (>${fastThreshold}km/h)") },
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
                            Icon(
                                imageVector = activity.icon,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = if (uiState.sessions.isEmpty()) {
                                "No recorded ${activity.nounPlural} yet"
                            } else {
                                "No ${activity.nounPlural} match this filter"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Record your ${activity.displayTitle.lowercase()} sessions to see detailed stats here",
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
                            Text("Start ${activity.displayTitle}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(
                    items = filteredSessions,
                    key = { it.id }
                ) { session ->
                    SessionCard(
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
