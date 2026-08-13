package com.example.smartcyclingtracker.ui.challenges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.engine.PhysicsEngine
import com.example.smartcyclingtracker.theme.*
import com.example.smartcyclingtracker.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChallengesScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge = uiState.latestChallenge

    // Get all sessions that are marked as challenge completions
    val completedChallenges = uiState.sessions.filter { it.isChallengeCompletion }.sortedByDescending { it.startTime }

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

                if (challenge == null || challenge.status == "COMPLETED" || challenge.status == "CANCELLED") {
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (challenge?.status == "COMPLETED") Color(0xFFFFD700) else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (challenge?.status == "COMPLETED") "Challenge Completed!" else if (challenge?.status == "CANCELLED") "Challenge Cancelled" else "Daily Challenges",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Next challenge arrives $nextChallengeCountdown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    ChallengeCard(
                        challenge = challenge,
                        onAccept = { viewModel.respondToChallenge(challenge, true) },
                        onDeny = { viewModel.respondToChallenge(challenge, false) }
                    )
                }
            }

            if (completedChallenges.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Completed Challenges History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(completedChallenges, key = { it.id }) { session ->
                    CompletedChallengeCard(session = session)
                }
            }
        }
    }
}

@Composable
fun ChallengeCard(
    challenge: com.example.smartcyclingtracker.data.local.entity.ChallengeEntity,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
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
                    text = "${challenge.period.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Challenge",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val targetStr = when(challenge.metric) {
                "DISTANCE" -> PhysicsEngine.formatDistance(challenge.targetValue)
                "SPEED" -> "${PhysicsEngine.formatSpeed(challenge.targetValue)} km/h"
                "CALORIES" -> "${"%.0f".format(challenge.targetValue)} kcal"
                else -> challenge.targetValue.toString()
            }
            val progressStr = when(challenge.metric) {
                "DISTANCE" -> PhysicsEngine.formatDistance(challenge.currentProgress)
                "SPEED" -> "${PhysicsEngine.formatSpeed(challenge.currentProgress)} km/h"
                "CALORIES" -> "${"%.0f".format(challenge.currentProgress)} kcal"
                else -> challenge.currentProgress.toString()
            }
            Text(
                text = "${challenge.activityType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}: $targetStr",
                style = MaterialTheme.typography.bodyLarge,
                color = ElectricGreen
            )
            
            if (challenge.status == "PENDING") {
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
            } else if (challenge.status == "ACTIVE" || challenge.status == "ACCEPTED") {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (challenge.currentProgress / challenge.targetValue).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFFFD700),
                    trackColor = NavyDarker
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Progress: $progressStr / $targetStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CompletedChallengeCard(session: WorkoutSessionEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
    val date = dateFormat.format(Date(session.startTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
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
                    color = TextSecondary
                )
            }
        }
    }
}
