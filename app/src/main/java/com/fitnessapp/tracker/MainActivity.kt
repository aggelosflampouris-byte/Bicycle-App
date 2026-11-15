package com.fitnessapp.tracker

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.ThemeMode
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.theme.LocalActivityTheme
import com.fitnessapp.tracker.theme.SmartTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var authRepository: com.fitnessapp.tracker.data.remote.AuthRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // We only request foreground location and notifications on startup.
        // Background location should be requested contextually later if needed.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request all required runtime permissions together
        requestAllPermissions()

        // Schedule WorkManager for routine check (Daily)
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.fitnessapp.tracker.worker.RoutineReminderWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RoutineReminder",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )



        // Schedule WorkManager for Inactivity Workout Reminder (Daily Check)
        val reminderPeriodicRequest = androidx.work.PeriodicWorkRequestBuilder<com.fitnessapp.tracker.worker.WorkoutReminderWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PeriodicWorkoutReminder",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            reminderPeriodicRequest
        )
        
        handleIntent(intent)

        setContent {
            // Determine start destination reactively on startup
            val initialDestination by produceState<String?>(initialValue = null) {
                val user = userDao.getUserFlow().firstOrNull()
                val isTracking = com.fitnessapp.tracker.service.OutdoorTrackingService.trackingState.value.isTracking

                value = when {
                    user == null -> Screen.Onboarding.route
                    !authRepository.isUserLoggedIn -> Screen.Auth.route
                    isTracking -> Screen.LiveTracking.route
                    else -> Screen.Main.route
                }
            }

            // Collect theme preference reactively
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            val activityType by settingsRepository.activityType.collectAsStateWithLifecycle("CYCLING")
            val lockPortraitModeEnabled by settingsRepository.isLockPortraitModeEnabled.collectAsStateWithLifecycle(true)

            LaunchedEffect(lockPortraitModeEnabled) {
                val targetOrientation = if (lockPortraitModeEnabled) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (requestedOrientation != targetOrientation) {
                    requestedOrientation = targetOrientation
                }
            }

            SmartTrackTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalActivityTheme provides activityType) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {

                        initialDestination?.let { destination ->
                            AppNavGraph(startDestination = destination)
                        }
                    }
                }
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == com.fitnessapp.tracker.service.IntentActions.RECOMMENDED_WORKOUT) {
            val recommendedActivity = intent.getStringExtra("RECOMMENDED_ACTIVITY") ?: "CYCLING"
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.cancel(com.fitnessapp.tracker.service.NotificationHelper.REMINDER_NOTIFICATION_ID)

            lifecycleScope.launch(Dispatchers.IO) {
                settingsRepository.setActivityType(recommendedActivity)
            }
        }
    }
}
