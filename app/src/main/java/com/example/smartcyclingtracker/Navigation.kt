package com.example.smartcyclingtracker

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartcyclingtracker.ui.chat.AiChatScreen
import com.example.smartcyclingtracker.ui.dashboard.DashboardScreen
import com.example.smartcyclingtracker.ui.main.MainScreen
import com.example.smartcyclingtracker.ui.onboarding.OnboardingScreen
import com.example.smartcyclingtracker.ui.summary.PostWorkoutSummaryScreen
import com.example.smartcyclingtracker.ui.tracking.LiveTrackingScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Main : Screen("main")
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
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
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
                    if (sessionId > 0) {
                        navController.navigate(Screen.PostWorkoutSummary.createRoute(sessionId)) {
                            popUpTo(Screen.LiveTracking.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.LiveTracking.route) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
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
                onBack = { navController.popBackStack() },
                triggerAnalysis = true
            )
        }
    }
}
