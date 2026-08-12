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

    const val CHALLENGE_CHANNEL_ID = "challenge_channel"
    const val CHALLENGE_NOTIFICATION_ID = 3003

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
            
            val challengeChannel = NotificationChannel(
                CHALLENGE_CHANNEL_ID,
                "Challenges",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily fitness challenges"
            }
            
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(trackingChannel)
            manager.createNotificationChannel(updateChannel)
            manager.createNotificationChannel(challengeChannel)
        }
    }

    fun buildTrackingNotification(
        context: Context,
        speedKmh: Double,
        distanceMeters: Double,
        durationSeconds: Long,
        isPaused: Boolean,
        activeChallenge: com.example.smartcyclingtracker.data.local.entity.ChallengeEntity? = null
    ): Notification {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("smartcyclingtracker://live_tracking"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, CyclingTrackingService::class.java).apply { 
            action = CyclingTrackingService.ACTION_TOGGLE_PAUSE 
        }
        val pausePendingIntent = PendingIntent.getService(
            context, 1, pauseIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lapIntent = Intent(context, CyclingTrackingService::class.java).apply { 
            action = CyclingTrackingService.ACTION_LAP 
        }
        val lapPendingIntent = PendingIntent.getService(
            context, 2, lapIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finishIntent = Intent(context, CyclingTrackingService::class.java).apply { 
            action = CyclingTrackingService.ACTION_STOP 
        }
        val finishPendingIntent = PendingIntent.getService(
            context, 3, finishIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(
                if (activeChallenge != null) {
                    if (isPaused) "⏸️ Challenge Paused" else "🏅 Active Challenge!"
                } else {
                    if (isPaused) context.getString(R.string.notification_title_paused) else context.getString(R.string.notification_title_active)
                }
            )
            .setContentText(
                if (activeChallenge != null) {
                    "${activeChallenge.metric}: ${activeChallenge.currentProgress} / ${activeChallenge.targetValue}"
                } else {
                    context.getString(R.string.notification_stats, PhysicsEngine.formatSpeed(speedKmh), PhysicsEngine.formatDistance(distanceMeters))
                }
            )
            .setSubText(context.getString(R.string.notification_duration, PhysicsEngine.formatDuration(durationSeconds)))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (isPaused) context.getString(R.string.action_resume) else context.getString(R.string.action_pause),
            pausePendingIntent
        )
        if (activeChallenge != null) {
            val cancelIntent = Intent(context, ChallengeActionReceiver::class.java).apply {
                action = "ACTION_CANCEL_CHALLENGE"
                putExtra("CHALLENGE_ID", activeChallenge.id)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context, 4, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            
            // Still allow them to finish the workout, which evaluates the challenge
            builder.addAction(android.R.drawable.ic_menu_save, context.getString(R.string.action_finish), finishPendingIntent)
        } else {
            if (!isPaused) {
                builder.addAction(android.R.drawable.ic_menu_add, context.getString(R.string.action_lap), lapPendingIntent)
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_finish), finishPendingIntent)
        }

        return builder.build()
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

    fun showNewChallengeNotification(context: Context, id: Long, challenge: com.example.smartcyclingtracker.data.local.entity.ChallengeEntity) {
        val acceptIntent = Intent(context, ChallengeActionReceiver::class.java).apply {
            action = "ACTION_ACCEPT_CHALLENGE"
            putExtra("CHALLENGE_ID", id)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyIntent = Intent(context, ChallengeActionReceiver::class.java).apply {
            action = "ACTION_DENY_CHALLENGE"
            putExtra("CHALLENGE_ID", id)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            context, 1, denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Your new ${challenge.period.lowercase()} challenge is ready: ${challenge.activityType} - ${challenge.metric} = ${challenge.targetValue}!"

        val notification = NotificationCompat.Builder(context, CHALLENGE_CHANNEL_ID)
            .setContentTitle("🏅 New Challenge!")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Deny", denyPendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(CHALLENGE_NOTIFICATION_ID, notification)
    }
}
