package com.example.smartcyclingtracker.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.theme.*
import kotlin.math.absoluteValue

@Composable
fun ProgressScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time Filter Segmented Control
        TimeFilterSelector(
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = { viewModel.setFilter(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Distance Over Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedBarChart(data = uiState.chartData)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Performance vs Previous Period",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        // Comparison Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ComparisonCard(
                modifier = Modifier.weight(1f),
                label = "Distance",
                value = "${"%.1f".format(uiState.totalDistanceKm)} km",
                diffPercent = uiState.distanceDiffPercent
            )
            ComparisonCard(
                modifier = Modifier.weight(1f),
                label = "Avg Speed",
                value = "${"%.1f".format(uiState.avgSpeedKmh)} km/h",
                diffPercent = uiState.speedDiffPercent
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ComparisonCard(
                modifier = Modifier.weight(1f),
                label = "Calories",
                value = "${"%.0f".format(uiState.totalCalories)} kcal",
                diffPercent = uiState.caloriesDiffPercent
            )
            // Empty space for layout balance
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun TimeFilterSelector(
    selectedFilter: TimeFilter,
    onFilterSelected: (TimeFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TimeFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onFilterSelected(filter) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSelected) ElectricGreen else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ComparisonCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    diffPercent: Double?
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (diffPercent != null) {
                val isPositive = diffPercent >= 0
                val icon = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                val color = if (isPositive) ElectricGreen else SpeedRed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${"%.1f".format(diffPercent.absoluteValue)}%",
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "No previous data",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
