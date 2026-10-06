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
        shape = RoundedCornerShape(8.dp),
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
fun TrainingPlanGoalDialog(
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
        shape = RoundedCornerShape(8.dp)
    )
}

