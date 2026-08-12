package com.example.smartcyclingtracker

import android.Manifest
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
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.local.ThemeMode
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.theme.LocalActivityTheme
import com.example.smartcyclingtracker.theme.SmartCyclingTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import android.app.NotificationManager
import com.example.smartcyclingtracker.data.local.dao.ChallengeDao
import com.example.smartcyclingtracker.data.local.entity.ChallengeStatus
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var challengeDao: ChallengeDao

    private var startDestination = Screen.Onboarding.route

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // We only request foreground location and notifications on startup.
        // Background location should be requested contextually later if needed.
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Background location result handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request all required runtime permissions together
        requestAllPermissions()

        // Schedule WorkManager for routine check (Daily)
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.smartcyclingtracker.worker.RoutineReminderWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RoutineReminder",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        
        // Schedule WorkManager for Challenge Check (Daily)
        val challengeRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.smartcyclingtracker.worker.ChallengeWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyChallenge",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            challengeRequest
        )
        
        // Also fire it immediately just in case they don't have an active one
        val immediateChallengeRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.smartcyclingtracker.worker.ChallengeWorker>().build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "ImmediateChallenge",
            androidx.work.ExistingWorkPolicy.KEEP,
            immediateChallengeRequest
        )
        
        handleIntent(intent)

        // Determine start destination based on whether user has been set up
        lifecycleScope.launch {
            val user = userDao.getUserFlow().firstOrNull()
            val isTracking = com.example.smartcyclingtracker.service.CyclingTrackingService.trackingState.value.isTracking
            
            startDestination = when {
                user == null -> Screen.Onboarding.route
                isTracking -> Screen.LiveTracking.route
                else -> Screen.ActivitySelection.route
            }

            setContent {
                // Collect theme preference reactively
                val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
                val isSystemDark = isSystemInDarkTheme()
                val isDark = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemDark
                }

                val activityType by settingsRepository.activityType.collectAsStateWithLifecycle("CYCLING")

                SmartCyclingTrackerTheme(darkTheme = isDark) {
                    CompositionLocalProvider(LocalActivityTheme provides activityType) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            CyclingNavGraph(startDestination = startDestination)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_ACCEPT_CHALLENGE") {
            val challengeId = intent.getLongExtra("CHALLENGE_ID", -1L)
            val activityType = intent.getStringExtra("ACTIVITY_TYPE") ?: "CYCLING"
            
            if (challengeId != -1L) {
                // Cancel notification
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.cancel(com.example.smartcyclingtracker.service.NotificationHelper.CHALLENGE_NOTIFICATION_ID)
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val challenge = challengeDao.getLatestChallenge()
                    if (challenge != null && challenge.id == challengeId) {
                        challengeDao.updateChallenge(challenge.copy(status = ChallengeStatus.ACCEPTED.name))
                    }
                    settingsRepository.setActivityType(activityType)
                }
            }
        }
    }
}
