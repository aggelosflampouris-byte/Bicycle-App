package com.example.smartcyclingtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class CyclingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid with compliant user agent (avoids OSM 403 Forbidden)
        Configuration.getInstance().apply {
            load(
                this@CyclingApp,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
            )
            userAgentValue = "VeloTrack-CyclingTracker/1.0 (Linux; Android; SmartCyclingApp)"
        }
    }
}
