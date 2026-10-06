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
fun TrophiesAndRecordsCard(
    records: List<com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity>,
    activityType: String
) {
    if (records.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, ChallengeGold.copy(alpha = 0.4f))
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
                        tint = ChallengeGold,
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
                        color = ChallengeGold.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${records.size}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ChallengeGold
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
                                color = ChallengeGold
                            )
                        }
                    }
                }
            }
        }
    }
}

