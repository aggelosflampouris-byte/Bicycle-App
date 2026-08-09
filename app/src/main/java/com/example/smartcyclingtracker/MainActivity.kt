package com.example.smartcyclingtracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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

    // Unified permission launcher to avoid dialog cancellation
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Background location result handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all required runtime permissions together
        requestAllPermissions()

        // Determine start destination based on whether user has been set up
        lifecycleScope.launch {
            val user = userDao.getUserFlow().firstOrNull()
            startDestination = if (user != null) Screen.Main.route else Screen.Onboarding.route

            setContent {
                // Collect theme preference reactively
                val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
                val isSystemDark = isSystemInDarkTheme()
                val isDark = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemDark
                }

                SmartCyclingTrackerTheme(darkTheme = isDark) {
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

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
