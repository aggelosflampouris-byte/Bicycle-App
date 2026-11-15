package com.fitnessapp.tracker.ui.dashboard.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.tracker.theme.DeepNavy
import com.fitnessapp.tracker.theme.ElectricGreen
@Composable
fun StartWorkoutButton(activityType: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricGreen,
            contentColor = DeepNavy
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        val icon = when (activityType) {
            "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
            "JOGGING" -> Icons.AutoMirrored.Filled.DirectionsRun
            "CYCLING" -> Icons.AutoMirrored.Filled.DirectionsBike
            else -> Icons.Default.FitnessCenter
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        val actionText = when (activityType) {
            "WALKING" -> "START WALKING"
            "JOGGING" -> "START JOGGING"
            "CYCLING" -> "START CYCLING"
            else -> "START WORKOUT"
        }
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 1.sp
        )
    }
}

