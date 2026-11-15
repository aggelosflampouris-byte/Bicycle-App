package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric
import com.fitnessapp.tracker.theme.DeepNavy
import com.fitnessapp.tracker.theme.ElectricGreen
import com.fitnessapp.tracker.theme.GlassBorder
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.SpeedRed
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
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

