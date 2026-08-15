package com.fitnessapp.tracker.ui.challenges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeMetric
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChallengesScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge = uiState.latestChallenge
    val completedChallenges = uiState.completedChallenges

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(top = 16.dp),
    ) {
        Text(
            text = "Challenges",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Current Challenge",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (challenge == null || challenge.status == ChallengeStatus.COMPLETED || challenge.status == ChallengeStatus.CANCELLED || challenge.status == ChallengeStatus.DENIED) {
                    // Compute a live countdown to the next noon
                    var nextChallengeCountdown by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            val now = Calendar.getInstance()
                            val nextNoon = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            val diffMs = nextNoon.timeInMillis - now.timeInMillis
                            val hours = diffMs / (1000 * 60 * 60)
                            val minutes = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
                            nextChallengeCountdown = when {
                                hours > 0  -> "in ${hours}h ${minutes}m"
                                minutes > 0 -> "in ${minutes}m"
                                else       -> "soon!"
                            }
                            delay(60_000L) // refresh every minute
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (challenge?.status == ChallengeStatus.COMPLETED) Color(0xFFFFD700) else TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = when (challenge?.status) {
                                            ChallengeStatus.COMPLETED -> "Challenge Completed! 🏆"
                                            ChallengeStatus.DENIED -> "Challenge Denied"
                                            ChallengeStatus.CANCELLED -> "Challenge Cancelled"
                                            else -> "Daily Challenges"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Next scheduled challenge arrives $nextChallengeCountdown",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.generateNewChallenge() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = DeepNavy
                                )
                            ) {
                                Text("🎯 Generate Challenge Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    ChallengeCard(
                        challenge = challenge,
                        onAccept = { viewModel.respondToChallenge(challenge, true) },
                        onDeny = { viewModel.respondToChallenge(challenge, false) },
                        onCancel = { viewModel.cancelChallenge(challenge) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Completed Challenges History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (completedChallenges.isNotEmpty()) {
                        Text(
                            text = "${completedChallenges.size} Won",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (completedChallenges.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No completed challenges yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Accept a challenge and crush it during your workouts to earn trophies!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(completedChallenges, key = { it.id }) { challengeItem ->
                    CompletedChallengeCard(challenge = challengeItem)
                }
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
        border = BorderStroke(1.dp, Color(0xFFFFD700))
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
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${challenge.period.name.lowercase().replaceFirstChar { it.uppercase() }} Challenge",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val targetStr = when (challenge.metric) {
                ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.targetValue)
                ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.targetValue)} km/h"
                ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.targetValue)} kcal"
            }
            val progressStr = when (challenge.metric) {
                ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.currentProgress)
                ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.currentProgress)} km/h"
                ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.currentProgress)} kcal"
            }
            Text(
                text = "${challenge.activityType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}: $targetStr",
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
                    color = Color(0xFFFFD700),
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

@Composable
fun CompletedChallengeCard(challenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
    val date = dateFormat.format(Date(challenge.completedAt ?: challenge.createdAt))

    val targetStr = when (challenge.metric) {
        ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.targetValue)
        ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.targetValue)} km/h"
        ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.targetValue)} kcal"
    }
    val progressStr = when (challenge.metric) {
        ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.currentProgress)
        ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.currentProgress)} km/h"
        ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.currentProgress)} kcal"
    }

    val periodStr = challenge.period.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val activityStr = challenge.activityType.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$periodStr $activityStr",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0xFFFFD700).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFFFD700))
                    ) {
                        Text(
                            text = "COMPLETED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Goal: $targetStr • Reached: $progressStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Completed: $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
