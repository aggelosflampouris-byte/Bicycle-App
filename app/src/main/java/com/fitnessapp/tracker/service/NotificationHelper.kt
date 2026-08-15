package com.fitnessapp.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fitnessapp.tracker.MainActivity
import com.fitnessapp.tracker.R
import com.fitnessapp.tracker.engine.PhysicsEngine

object NotificationHelper {
    const val CHANNEL_ID = "cycling_tracker_channel"
    const val NOTIFICATION_ID = 1001

    const val UPDATE_CHANNEL_ID = "update_channel"
    const val UPDATE_NOTIFICATION_ID = 2002

    const val CHALLENGE_CHANNEL_ID = "challenge_channel"
    const val CHALLENGE_NOTIFICATION_ID = 3003

    const val REMINDER_CHANNEL_ID = "workout_reminder_channel"
    const val REMINDER_NOTIFICATION_ID = 4004

    const val ROUTINE_CHANNEL_ID = "routine_reminder_channel"
    const val ROUTINE_NOTIFICATION_ID = 5005

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
            manager.createNotificationChannel(updateChannel)
            manager.createNotificationChannel(challengeChannel)
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(routineChannel)
        }
    }

    fun buildTrackingNotification(
        context: Context,
        speedKmh: Double,
        distanceMeters: Double,
        durationSeconds: Long,
        isPaused: Boolean,
        activeChallenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity? = null,
        currentLap: Int = 1
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
                action = IntentActions.CANCEL_CHALLENGE
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
                builder.addAction(android.R.drawable.ic_menu_add, "Lap $currentLap", lapPendingIntent)
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

    fun showNewChallengeNotification(context: Context, id: Long, challenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity) {
        // Ensure channel exists – this is a no-op if already registered
        createNotificationChannel(context)

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            action = IntentActions.ACCEPT_CHALLENGE
            putExtra("CHALLENGE_ID", id)
            putExtra("ACTIVITY_TYPE", challenge.activityType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyIntent = Intent(context, ChallengeActionReceiver::class.java).apply {
            action = IntentActions.DENY_CHALLENGE
            putExtra("CHALLENGE_ID", id)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            context, 1, denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Your new ${challenge.period.name.lowercase()} challenge is ready: ${challenge.activityType} - ${challenge.metric.name} = ${challenge.targetValue}!"

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
        manager.notify(CHALLENGE_NOTIFICATION_ID + 100, notification)
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

    const val CHALLENGE_COMPLETED_NOTIFICATION_ID = 3005

    fun showChallengeCompletedNotification(
        context: Context,
        challenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = IntentActions.VIEW_CHALLENGES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetStr = when (challenge.metric) {
            com.fitnessapp.tracker.data.local.entity.ChallengeMetric.DISTANCE -> PhysicsEngine.formatDistance(challenge.targetValue)
            com.fitnessapp.tracker.data.local.entity.ChallengeMetric.SPEED    -> "${PhysicsEngine.formatSpeed(challenge.targetValue)} km/h"
            com.fitnessapp.tracker.data.local.entity.ChallengeMetric.CALORIES -> "${"%.0f".format(challenge.targetValue)} kcal"
        }

        val periodStr = challenge.period.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        val activityStr = challenge.activityType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

        val title = "🏆 Challenge Completed!"
        val message = "Congratulations! You crushed your $periodStr $activityStr challenge: $targetStr!"

        val notification = NotificationCompat.Builder(context, CHALLENGE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(CHALLENGE_COMPLETED_NOTIFICATION_ID, notification)
    }
}
