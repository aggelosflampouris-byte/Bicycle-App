package com.fitnessapp.tracker.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.remote.EnvironmentContextRepository
import com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
import com.fitnessapp.tracker.engine.AcwrZone
import com.fitnessapp.tracker.engine.DigitalTwinEngine
import com.fitnessapp.tracker.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Digital Twin-aware workout reminder worker. Evaluates recovery readiness, ACWR load ratio,
 * and outdoor microclimate before recommending an appropriate sport or gentle rest/recovery.
 */
@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val workoutSessionDao: WorkoutSessionDao,
    private val userDao: UserDao,
    private val wellnessCheckInDao: WellnessCheckInDao,
    private val environmentContextRepository: EnvironmentContextRepository,
    private val settingsRepository: SettingsRepository,
    private val digitalTwinEngine: DigitalTwinEngine
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val recentSessions = workoutSessionDao.getRecentSessions(limit = 30)
            if (recentSessions.isEmpty()) return Result.success()

            val latestSession = recentSessions.first()
            val now = System.currentTimeMillis()
            val twoDaysMs = TimeUnit.DAYS.toMillis(2)
            if (now - latestSession.endTime < twoDaysMs) return Result.success()

            val user = userDao.getUser() ?: UserEntity()
            val latestCheckIn = wellnessCheckInDao.getRecentCheckIns(limit = 1).firstOrNull()
            val preferredSport = settingsRepository.activityType.first()
            val environment = try {
                environmentContextRepository.getEnvironmentContext()
            } catch (_: Exception) {
                EnvironmentSnapshot(isOfflineFallback = true)
            }

            val twinState = digitalTwinEngine.computeTwinState(
                sessions = recentSessions,
                user = user,
                latestCheckIn = latestCheckIn,
                environment = environment,
                preferredActivityType = preferredSport,
                nowMs = now
            )

            val frequentSport = ActivityType.fromString(
                recentSessions.groupingBy { it.activityType }.eachCount().maxByOrNull { it.value }?.key
                    ?: preferredSport
            )

            val needsRestOrGentleRecovery = twinState.readinessScore < 40 || twinState.acwrZone == AcwrZone.OVERTRAINING
            val recommendedActivity = if (needsRestOrGentleRecovery) ActivityType.WALKING else frequentSport
            val weatherHint = environment.bestWindowLabel?.let { " $it." } ?: ""

            val (title, message) = if (needsRestOrGentleRecovery) {
                "Twin Recovery Check-In" to
                    "Readiness is ${twinState.readinessScore}/100 (ACWR ${"%.2f".format(twinState.acwr)}). Prioritize rest or an easy restorative walk today.$weatherHint"
            } else {
                val sportName = recommendedActivity.displayTitle
                "Ready for $sportName?" to
                    "Digital Twin Readiness ${twinState.readinessScore}/100 • Body Battery ${twinState.bodyBattery}%. ${twinState.recommendedIntensity.label} looks great today.$weatherHint"
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
                    recommendedActivity = recommendedActivity.name,
                    title = title,
                    message = message
                )
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WorkoutReminderWorker", "Error executing workout reminder worker", e)
            Result.failure()
        }
    }
}
