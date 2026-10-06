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
        border = BorderStroke(1.dp, ChallengeGold)
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
                    tint = ChallengeGold,
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
                    color = ChallengeGold,
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

