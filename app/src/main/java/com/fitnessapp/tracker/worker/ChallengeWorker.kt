package com.fitnessapp.tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.engine.ChallengeGenerator
import com.fitnessapp.tracker.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

@HiltWorker
class ChallengeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val challengeDao: ChallengeDao,
    private val challengeGenerator: ChallengeGenerator,
    private val settingsRepository: com.fitnessapp.tracker.data.local.SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val challengesEnabled = settingsRepository.challengesEnabled.first()
        if (!challengesEnabled) {
            return Result.success()
        }

        val latest = challengeDao.getLatestChallenge()
        val activeChallenge = challengeDao.getActiveChallenge()
        
        // If there's an ongoing challenge, we don't generate a new one.
        if (activeChallenge != null && 
            (activeChallenge.status == ChallengeStatus.ACCEPTED.name || 
             activeChallenge.status == ChallengeStatus.ACTIVE.name)) {
            return Result.success()
        }
        
        // Check if there is a pending challenge that hasn't been answered yet.
        if (latest != null && latest.status == ChallengeStatus.PENDING.name) {
            val oneDayMs = 24 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - latest.createdAt < oneDayMs) {
                // There is already an active pending challenge from today
                return Result.success()
            }
        }
        
        // Generate a new challenge
        val newChallenge = challengeGenerator.generateNewChallenge()
        val id = challengeDao.insertChallenge(newChallenge)
        
        // Show notification only when POST_NOTIFICATIONS permission is granted (Android 13+)
        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (canNotify) {
            NotificationHelper.showNewChallengeNotification(context, id, newChallenge)
        }
        
        return Result.success()
    }
}
