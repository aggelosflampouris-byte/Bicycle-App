package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.theme.ChallengeGold
import com.fitnessapp.tracker.theme.DeepNavy
import com.fitnessapp.tracker.theme.GlassBorder
import com.fitnessapp.tracker.theme.NavyCard
import com.fitnessapp.tracker.theme.TextPrimary
import com.fitnessapp.tracker.theme.TextSecondary
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

