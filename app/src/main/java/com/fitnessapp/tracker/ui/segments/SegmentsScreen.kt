package com.fitnessapp.tracker.ui.segments

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.data.local.entity.SegmentEffortEntity
import com.fitnessapp.tracker.data.local.entity.SegmentEntity
import com.fitnessapp.tracker.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentsScreen(
    onBack: () -> Unit,
    viewModel: SegmentsViewModel = hiltViewModel()
) {
    val segmentsWithBest by viewModel.segmentsWithBest.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSegmentId by viewModel.activeSegmentId.collectAsStateWithLifecycle()
    val leaderboard by viewModel.selectedSegmentLeaderboard.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Local Segments & PRs 👑",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Segment", tint = ElectricGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        if (segmentsWithBest.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(NavyCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏁", fontSize = 32.sp)
                    }
                    Text(
                        "No Road Segments Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Bookmark your favorite hill climbs, sprint straights, or park loops to automatically record leaderboards and PRs every time you ride!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Segment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Tap a segment to view all historical attempts & rankings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(segmentsWithBest, key = { it.segment.id }) { item ->
                    val isExpanded = activeSegmentId == item.segment.id
                    SegmentCard(
                        item = item,
                        isExpanded = isExpanded,
                        leaderboard = if (isExpanded) leaderboard else emptyList(),
                        onToggleExpand = {
                            if (isExpanded) viewModel.clearSelectedSegment()
                            else viewModel.selectSegment(item.segment.id)
                        },
                        onDelete = { viewModel.deleteSegment(item.segment) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSegmentDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, startLat, startLng, endLat, endLng, dist, ele ->
                viewModel.createSegment(name, startLat, startLng, endLat, endLng, dist, ele)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun SegmentCard(
    item: SegmentWithBest,
    isExpanded: Boolean,
    leaderboard: List<SegmentEffortEntity>,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    val seg = item.segment
    val best = item.bestEffort

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isExpanded) ElectricGreen.copy(alpha = 0.5f) else GlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = seg.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${"%.2f".format(seg.distanceMeters / 1000.0)} km • ${"%.1f".format(seg.avgGradientPct)}% Avg • ${item.totalAttempts} attempts",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (best != null) {
                    val mins = best.elapsedSeconds / 60
                    val secs = best.elapsedSeconds % 60
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(WarningAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("👑", fontSize = 14.sp)
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = GlassBorder)
                    Text(
                        "Leaderboard / All Attempts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VividCyan
                    )

                    if (leaderboard.isEmpty()) {
                        Text(
                            "No recorded efforts yet. Ride through this segment to post a time!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        leaderboard.forEachIndexed { index, effort ->
                            LeaderboardRow(rank = index + 1, effort = effort)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = SpeedRed),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Segment")
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, effort: SegmentEffortEntity) {
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }

    val mins = effort.elapsedSeconds / 60
    val secs = effort.elapsedSeconds % 60
    val timeStr = "%02d:%02d".format(mins, secs)
    val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(effort.dateMs))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeepNavy)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(medal, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Column {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rank == 1) WarningAmber else TextPrimary
                )
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Text(
            text = "${"%.1f".format(effort.avgSpeedKmh)} km/h",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ElectricGreen
        )
    }
}

@Composable
private fun CreateSegmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, startLat: Double, startLng: Double, endLat: Double, endLng: Double, dist: Double, ele: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startLat by remember { mutableStateOf("37.9838") }
    var startLng by remember { mutableStateOf("23.7275") }
    var endLat by remember { mutableStateOf("37.9950") }
    var endLng by remember { mutableStateOf("23.7350") }
    var distanceM by remember { mutableStateOf("1200") }
    var elevationM by remember { mutableStateOf("45") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Road Segment", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Segment Name") },
                    placeholder = { Text("e.g. Acropolis Hill Sprint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distanceM,
                        onValueChange = { distanceM = it },
                        label = { Text("Length (m)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = elevationM,
                        onValueChange = { elevationM = it },
                        label = { Text("Elevation (m)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sLat = startLat.toDoubleOrNull() ?: 0.0
                    val sLng = startLng.toDoubleOrNull() ?: 0.0
                    val eLat = endLat.toDoubleOrNull() ?: 0.0
                    val eLng = endLng.toDoubleOrNull() ?: 0.0
                    val dist = distanceM.toDoubleOrNull() ?: 1000.0
                    val ele = elevationM.toDoubleOrNull() ?: 0.0
                    onConfirm(name, sLat, sLng, eLat, eLng, dist, ele)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Segment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = NavyCard
    )
}
