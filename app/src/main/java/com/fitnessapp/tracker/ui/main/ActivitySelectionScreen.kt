package com.fitnessapp.tracker.ui.main


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.theme.*

@Composable
fun ActivitySelectionScreen(
    onActivitySelected: (ActivityType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ready to go?",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select your activity",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        ActivityCard(
            title = "Cycling",
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            onClick = { onActivitySelected(ActivityType.CYCLING) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        ActivityCard(
            title = "Walking",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            onClick = { onActivitySelected(ActivityType.WALKING) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        ActivityCard(
            title = "Jogging",
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            onClick = { onActivitySelected(ActivityType.JOGGING) }
        )
    }
}

@Composable
fun ActivityCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = ElectricGreen,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
