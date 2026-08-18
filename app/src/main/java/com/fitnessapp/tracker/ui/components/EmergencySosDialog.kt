package com.fitnessapp.tracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessapp.tracker.safety.CrashState
import com.fitnessapp.tracker.theme.*

@Composable
fun EmergencySosDialog(
    crashState: CrashState,
    emergencyContactName: String,
    emergencyContactPhone: String,
    onCancel: () -> Unit
) {
    if (crashState !is CrashState.Countdown && crashState !is CrashState.SosDispatched) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val isDispatched = crashState is CrashState.SosDispatched
    val secondsLeft = (crashState as? CrashState.Countdown)?.secondsRemaining ?: 0

    Dialog(
        onDismissRequest = { /* Cannot dismiss by clicking outside during emergency */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy.copy(alpha = 0.96f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulseScale),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(3.dp, SpeedRed),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pulsing Warning Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(SpeedRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SpeedRed,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Text(
                        text = if (isDispatched) "🚨 SOS BEACON DISPATCHED" else "⚠️ CRASH DETECTED!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = SpeedRed,
                        textAlign = TextAlign.Center
                    )

                    if (!isDispatched) {
                        Text(
                            text = "A severe impact was detected. Sending emergency SOS beacon with your GPS coordinates in:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        // Big Countdown Number
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(DeepNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$secondsLeft",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = SpeedRed
                            )
                        }

                        if (emergencyContactPhone.isNotBlank()) {
                            Text(
                                text = "Emergency Contact: $emergencyContactName ($emergencyContactPhone)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "An emergency distress SMS has been sent with your exact GPS coordinates. The alarm beacon will sound continuously.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cancel / "I'm OK" Button
                    Button(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGreen,
                            contentColor = DeepNavy
                        )
                    ) {
                        Text(
                            text = if (isDispatched) "STOP ALARM & I'M OK" else "I'M OK — CANCEL SOS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
