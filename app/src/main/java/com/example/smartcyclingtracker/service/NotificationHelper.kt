package com.example.smartcyclingtracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartcyclingtracker.MainActivity
import com.example.smartcyclingtracker.R
import com.example.smartcyclingtracker.engine.PhysicsEngine

object NotificationHelper {
    const val CHANNEL_ID = "cycling_tracker_channel"
    const val NOTIFICATION_ID = 1001

    const val UPDATE_CHANNEL_ID = "update_channel"
    const val UPDATE_NOTIFICATION_ID = 2002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_ID,
                "Cycling Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live GPS tracking notification"
                setShowBadge(false)
            }
            
            val updateChannel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new app versions"
            }
            
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(trackingChannel)
            manager.createNotificationChannel(updateChannel)
        }
    }

    fun buildTrackingNotification(
        context: Context,
        speedKmh: Double,
        distanceMeters: Double,
        durationSeconds: Long
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🚴 Smart Track Active")
            .setContentText(
                "Speed: ${PhysicsEngine.formatSpeed(speedKmh)} km/h  •  " +
                "Distance: ${PhysicsEngine.formatDistance(distanceMeters)}"
            )
            .setSubText("Duration: ${PhysicsEngine.formatDuration(durationSeconds)}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showUpdateNotification(context: Context, latestVersion: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setContentTitle("Update Available")
            .setContentText("Smart Track version $latestVersion is ready to install.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // built-in icon
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        // If POST_NOTIFICATIONS is denied on Android 13+, this will gracefully be ignored by the system
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
}
