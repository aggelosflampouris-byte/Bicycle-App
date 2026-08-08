package com.example.smartcyclingtracker.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcyclingtracker.theme.*
import com.example.smartcyclingtracker.ui.chat.AiChatScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardScreen
import com.example.smartcyclingtracker.ui.onboarding.OnboardingScreen

enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.DirectionsBike),
    AI_COACH("VeloCoach", Icons.Default.Psychology),
    HISTORY("Activities", Icons.Default.Assessment),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun MainScreen(
    onStartWorkout: () -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }

    Scaffold(
        containerColor = DeepNavy,
        bottomBar = {
            NavigationBar(
                containerColor = NavyDarker,
                contentColor = TextPrimary,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) ElectricGreen else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isSelected) ElectricGreen else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricGreen,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = ElectricGreen,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = ElectricGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.DASHBOARD -> {
                    DashboardScreen(
                        onStartWorkout = onStartWorkout,
                        onSessionClick = onSessionClick,
                        onOpenSettings = { currentTab = MainTab.PROFILE }
                    )
                }
                MainTab.AI_COACH -> {
                    AiChatScreen(
                        onBack = { currentTab = MainTab.DASHBOARD }
                    )
                }
                MainTab.HISTORY -> {
                    DashboardScreen(
                        onStartWorkout = onStartWorkout,
                        onSessionClick = onSessionClick,
                        onOpenSettings = { currentTab = MainTab.PROFILE }
                    )
                }
                MainTab.PROFILE -> {
                    OnboardingScreen(
                        onComplete = { currentTab = MainTab.DASHBOARD }
                    )
                }
            }
        }
    }
}
