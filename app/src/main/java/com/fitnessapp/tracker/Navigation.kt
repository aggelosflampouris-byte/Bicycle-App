package com.fitnessapp.tracker

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.fitnessapp.tracker.theme.LocalActivityTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitnessapp.tracker.ui.chat.AiChatScreen
import com.fitnessapp.tracker.ui.dashboard.DashboardScreen
import com.fitnessapp.tracker.ui.main.ActivitySelectionScreen
import com.fitnessapp.tracker.ui.main.MainScreen
import com.fitnessapp.tracker.ui.onboarding.OnboardingScreen
import com.fitnessapp.tracker.ui.settings.SettingsViewModel
import com.fitnessapp.tracker.ui.summary.PostWorkoutSummaryScreen
import com.fitnessapp.tracker.ui.tracking.LiveTrackingScreen

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object ActivitySelection : Screen("activity_selection")
    object Main : Screen("main")
    object LiveTracking : Screen("tracking")
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
        composable(Screen.Auth.route) {
            com.fitnessapp.tracker.ui.auth.AuthScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.ActivitySelection.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ActivitySelection.route) {
            val settingsViewModel = hiltViewModel<SettingsViewModel>()
            ActivitySelectionScreen(
                onActivitySelected = { activityType ->
                    settingsViewModel.setActivityType(activityType.name)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.ActivitySelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Main.route
        ) {
            MainScreen(
                onStartWorkout = {
                    navController.navigate(Screen.LiveTracking.route)
                },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.PostWorkoutSummary.createRoute(sessionId))
                },
                onOpenSettings = {
                    navController.navigate(Screen.Onboarding.route)
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.LiveTracking.route,
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "smartcyclingtracker://live_tracking" })
        ) {
            // Read the activity type chosen on ActivitySelectionScreen (stored in LocalActivityTheme)
            val activityType = LocalActivityTheme.current
            LiveTrackingScreen(
                activityType = activityType,
                onTrackingFinished = { sessionId ->
                    if (sessionId > 0) {
                        navController.navigate(Screen.PostWorkoutSummary.createRoute(sessionId)) {
                            popUpTo(Screen.LiveTracking.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.ActivitySelection.route) {
                            popUpTo(Screen.LiveTracking.route) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.ActivitySelection.route) {
                            popUpTo(Screen.LiveTracking.route) { inclusive = true }
                        }
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
                onAskPersonalCoach = {
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
