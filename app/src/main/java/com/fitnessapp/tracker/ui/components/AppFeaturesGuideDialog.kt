package com.fitnessapp.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fitnessapp.tracker.theme.*

@Composable
fun AppFeaturesGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElectricGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "App Features Guide",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Discover everything Smart Track offers",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Feature List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureGuideItem(
                        icon = "🚴",
                        title = "Real-Time Tracking & HUD",
                        description = "High-precision GPS telemetry with live speed, distance, elevation gain, slope %, estimated power output (W/kg), split laps, and integrated maps."
                    )
                    FeatureGuideItem(
                        icon = "🏆",
                        title = "Personal Records & Trophies",
                        description = "Automatically detects all-time personal bests (Fastest 1km, 5km, 10km, 20km, 50km, Longest Distance & Duration, Highest Elevation, Top Speed) per activity."
                    )
                    FeatureGuideItem(
                        icon = "🤖",
                        title = "AI Personal Fitness Coach",
                        description = "Powered by AI with 4 customizable coaching personas. Provides adaptive weekly training plans, post-ride tactical debriefs, and pre-ride weather/nutrition advice."
                    )
                    FeatureGuideItem(
                        icon = "🎯",
                        title = "Workout Goals & Challenges",
                        description = "Set Daily, Weekly, or Monthly distance or calorie goals with 5% Auto-Improve progression. Complete AI-generated daily workout challenges to build lasting streaks."
                    )
                    FeatureGuideItem(
                        icon = "🎙️",
                        title = "Hands-Free Voice AI Coach",
                        description = "Talk to your AI Coach during live workouts and in chat. Uses speech-to-text recognition and delivers immediate audio answers via text-to-speech in English, Greek, German, French, or Russian."
                    )
                    FeatureGuideItem(
                        icon = "🔋",
                        title = "Smart Recovery & Sleep Advisor",
                        description = "Calculates EPOC/TSS training stress and 7-day cumulative fatigue to give accurate recovery countdowns, sleep targets, and hydration recommendations on the Dashboard and Post-Workout Summary."
                    )
                    FeatureGuideItem(
                        icon = "🔒",
                        title = "Hardware-Backed Privacy",
                        description = "100% of your GPS location traces are encrypted on-device using Android KeyStore AES-256-GCM hardware protection. Cloud synchronization scrubs location data for complete privacy."
                    )
                    FeatureGuideItem(
                        icon = "💾",
                        title = "Encrypted Backups & GPX Export",
                        description = "Export full password-encrypted offline .backup files to safeguard your fitness history. Export GPX files after any workout to import into Strava, Garmin, or Komoot."
                    )
                    FeatureGuideItem(
                        icon = "🔄",
                        title = "Seamless In-App Updater",
                        description = "Stay up to date with new features and improvements with direct GitHub Release updates and real-time download progress."
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGreen,
                        contentColor = DeepNavy
                    )
                ) {
                    Text("Got it!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FeatureGuideItem(
    icon: String,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepNavy.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
