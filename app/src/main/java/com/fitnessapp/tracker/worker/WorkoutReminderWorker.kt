package com.fitnessapp.tracker.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val workoutSessionDao: WorkoutSessionDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val recentSessions = workoutSessionDao.getRecentSessions(limit = 10)
            if (recentSessions.isEmpty()) {
                return Result.success()
            }

            val latestSession = recentSessions.first()
            val now = System.currentTimeMillis()
            val twoDaysMs = TimeUnit.DAYS.toMillis(2)

            // Check if at least 2 days have passed since the latest workout session
            if (now - latestSession.endTime >= twoDaysMs) {
                // Determine a smart workout activity recommendation
                val recommendedActivity = getRecommendedActivity(recentSessions.map { it.activityType })
                
                val (title, message) = when (recommendedActivity) {
                    "CYCLING" -> Pair(
                        "🚴 Ready for a ride?",
                        "It's been 2 days since your last workout. How about a cycling session today? Tap to hit the road!"
                    )
                    "JOGGING" -> Pair(
                        "🏃 Time for a run?",
                        "It's been 2 days since your last workout. A refreshing jog is waiting for you! Tap to start."
                    )
                    "WALKING" -> Pair(
                        "🚶 Let's go for a walk!",
                        "It's been 2 days since your last workout. Step outside for a brisk walk today! Tap to track."
                    )
                    else -> Pair(
                        "💪 Time to workout!",
                        "It's been 2 days since your last workout session. Stay active and keep your streak going! Tap to start."
                    )
                }

                val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (canNotify) {
                    NotificationHelper.showWorkoutReminderNotification(
                        context = context,
                        recommendedActivity = recommendedActivity,
                        title = title,
                        message = message
                    )
                }
            }

            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WorkoutReminderWorker", "Error executing workout reminder worker", e)
            return Result.failure()
        }
    }

    private fun getRecommendedActivity(recentActivityTypes: List<String>): String {
        if (recentActivityTypes.isEmpty()) return "CYCLING"
        
        // Count frequencies of recent activity types
        val counts = recentActivityTypes.groupingBy { it }.eachCount()
        return counts.maxByOrNull { it.value }?.key ?: "CYCLING"
    }
}
