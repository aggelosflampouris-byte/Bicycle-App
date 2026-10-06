package com.fitnessapp.tracker.ui.dashboard.components
import com.fitnessapp.tracker.ui.dashboard.*

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


import com.fitnessapp.tracker.ui.dashboard.components.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineConfigBottomSheet(
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

