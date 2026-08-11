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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

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

        // Schedule daily routine check
        com.example.smartcyclingtracker.service.RoutineScheduler.scheduleRoutineCheck(this)

        // Determine start destination based on whether user has been set up
        lifecycleScope.launch {
            val user = userDao.getUserFlow().firstOrNull()
            startDestination = if (user != null) Screen.ActivitySelection.route else Screen.Onboarding.route

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
}
