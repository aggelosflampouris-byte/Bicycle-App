package com.example.smartcyclingtracker

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.ui.chat.AiChatScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardScreen
import com.example.smartcyclingtracker.ui.onboarding.OnboardingScreen
import com.example.smartcyclingtracker.ui.summary.PostWorkoutSummaryScreen
import com.example.smartcyclingtracker.ui.tracking.LiveTrackingScreen
import com.example.smartcyclingtracker.ui.onboarding.OnboardingViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object LiveTracking : Screen("live_tracking")
    object PostWorkoutSummary : Screen("summary/{sessionId}") {
        fun createRoute(sessionId: Long) = "summary/$sessionId"
    }
    object AiChat : Screen("ai_chat/{sessionId}") {
        fun createRoute(sessionId: Long) = "ai_chat/$sessionId"
    }
}

@Composable
fun CyclingNavGraph(
    startDestination: String = Screen.Onboarding.route
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartWorkout = {
                    navController.navigate(Screen.LiveTracking.route)
                },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.PostWorkoutSummary.createRoute(sessionId))
                },
                onOpenSettings = {
                    navController.navigate(Screen.Onboarding.route)
                }
            )
        }

        composable(Screen.LiveTracking.route) {
            LiveTrackingScreen(
                onTrackingFinished = { sessionId ->
                    navController.navigate(Screen.PostWorkoutSummary.createRoute(sessionId)) {
                        popUpTo(Screen.LiveTracking.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.PostWorkoutSummary.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
            PostWorkoutSummaryScreen(
                sessionId = sessionId,
                onAskVeloCoach = {
                    navController.navigate(Screen.AiChat.createRoute(sessionId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AiChat.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            AiChatScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
