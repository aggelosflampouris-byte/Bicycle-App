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
fun RoutineProgressCard(
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

