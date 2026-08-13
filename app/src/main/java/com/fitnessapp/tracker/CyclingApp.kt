package com.fitnessapp.tracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class CyclingApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid with compliant user agent (avoids OSM 403 Forbidden)
        OsmConfiguration.getInstance().apply {
            load(
                this@CyclingApp,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
            )
            userAgentValue = "com.fitnessapp.tracker/1.0 (aggelosflampouris)"
        }
    }
}
