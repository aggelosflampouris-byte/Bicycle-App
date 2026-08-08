package com.example.smartcyclingtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class CyclingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid with app context and user agent
        Configuration.getInstance().apply {
            load(
                this@CyclingApp,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
            )
            userAgentValue = packageName
        }
    }
}
