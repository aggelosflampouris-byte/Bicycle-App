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

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cycling Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live GPS tracking notification"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
            .setContentTitle("🚴 VeloTrack Active")
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
}
