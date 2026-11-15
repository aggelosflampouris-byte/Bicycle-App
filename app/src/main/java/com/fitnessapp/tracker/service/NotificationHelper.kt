package com.fitnessapp.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.fitnessapp.tracker.MainActivity
import com.fitnessapp.tracker.R
import com.fitnessapp.tracker.engine.PhysicsEngine

object NotificationHelper {
    const val CHANNEL_ID = "cycling_tracker_channel"
    const val NOTIFICATION_ID = 1001

    const val REMINDER_CHANNEL_ID = "workout_reminder_channel"
    const val REMINDER_NOTIFICATION_ID = 4004

    const val ROUTINE_CHANNEL_ID = "routine_reminder_channel"
    const val ROUTINE_NOTIFICATION_ID = 5005

    fun createNotificationChannel(context: Context) {
        val trackingChannel = NotificationChannel(
            CHANNEL_ID,
            "Workout Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live GPS tracking notification"
            setShowBadge(false)
        }

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Workout Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders to stay active and workout"
        }

        val routineChannel = NotificationChannel(
            ROUTINE_CHANNEL_ID,
            "Routine Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for routine goals and streaks"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(routineChannel)
    }

    fun buildTrackingNotification(
        context: Context,
        speedKmh: Double,
        distanceMeters: Double,
        durationSeconds: Long,
        isPaused: Boolean,
        currentLap: Int = 1
    ): Notification {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "smarttrack://live_tracking".toUri(),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            context, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lapIntent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_LAP
        }
        val lapPendingIntent = PendingIntent.getService(
            context, 2, lapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finishIntent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_STOP
        }
        val finishPendingIntent = PendingIntent.getService(
            context, 3, finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(
                if (isPaused) context.getString(R.string.notification_title_paused)
                else context.getString(R.string.notification_title_active)
            )
            .setContentText(
                context.getString(
                    R.string.notification_stats,
                    PhysicsEngine.formatSpeed(speedKmh),
                    PhysicsEngine.formatDistance(distanceMeters)
                )
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
        if (!isPaused) {
            builder.addAction(android.R.drawable.ic_menu_add, "Lap $currentLap", lapPendingIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_finish), finishPendingIntent)

        return builder.build()
    }

    fun showRoutineReminderNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 5, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ROUTINE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(ROUTINE_NOTIFICATION_ID, notification)
    }

    fun showWorkoutReminderNotification(
        context: Context,
        recommendedActivity: String,
        title: String,
        message: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = IntentActions.RECOMMENDED_WORKOUT
            putExtra("RECOMMENDED_ACTIVITY", recommendedActivity)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}
