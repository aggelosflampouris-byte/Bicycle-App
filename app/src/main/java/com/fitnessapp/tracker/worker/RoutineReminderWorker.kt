package com.fitnessapp.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessapp.tracker.MainActivity
import com.fitnessapp.tracker.data.local.RoutineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

@HiltWorker
class RoutineReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val routineRepository: RoutineRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Check routine progress for all routines
            val routines = routineRepository.getAllRoutines()
            
            val now = System.currentTimeMillis()
            
            for (routine in routines) {
                // Since getRoutineProgressFlow requires activityType, we can either use it or calculate manually.
                // It's safer to fetch the specific progress flow for this routine's activity type
                val progress = routineRepository.getRoutineProgressFlow(routine.activityType).firstOrNull() ?: continue
                
                if (!progress.isCompleted) {
                    val timeLeftMillis = progress.routine.currentPeriodEnd - now
                    
                    // If less than 24 hours left, notify the user
                    if (timeLeftMillis > 0 && timeLeftMillis <= TimeUnit.HOURS.toMillis(24)) {
                        val remaining = String.format(java.util.Locale.US, "%.1f", progress.routine.targetValue - progress.currentValue)
                        val unit = if (progress.routine.metric == "DISTANCE") "km" else "kcals"
                        val interval = progress.routine.interval.lowercase()
                        val activityName = progress.routine.activityType.lowercase()
                        
                        showNotification(
                            "Don't break your streak! \uD83D\uDCAA",
                            "You have $remaining $unit left for your $interval $activityName routine. Get out there!"
                        )
                    }
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "routine_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Routine Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(2001, notification)
    }
}
