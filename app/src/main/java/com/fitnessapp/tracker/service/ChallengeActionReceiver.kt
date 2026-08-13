package com.fitnessapp.tracker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChallengeActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var challengeDao: ChallengeDao

    override fun onReceive(context: Context, intent: Intent) {
        val challengeId = intent.getLongExtra("CHALLENGE_ID", -1L)
        if (challengeId == -1L) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NotificationHelper.CHALLENGE_NOTIFICATION_ID)

        CoroutineScope(Dispatchers.IO).launch {
            val challenge = challengeDao.getLatestChallenge()
            if (challenge != null && challenge.id == challengeId) {
                when (intent.action) {
                    "ACTION_ACCEPT_CHALLENGE" -> {
                        challengeDao.updateChallenge(challenge.copy(status = ChallengeStatus.ACCEPTED.name))
                    }
                    "ACTION_DENY_CHALLENGE" -> {
                        challengeDao.updateChallenge(challenge.copy(status = ChallengeStatus.DENIED.name))
                    }
                    "ACTION_CANCEL_CHALLENGE" -> {
                        challengeDao.updateChallenge(challenge.copy(status = ChallengeStatus.CANCELLED.name))
                        // Also notify the active service to drop it
                        val stopChallengeIntent = Intent(context, CyclingTrackingService::class.java).apply {
                            action = "ACTION_CANCEL_CHALLENGE"
                        }
                        context.startService(stopChallengeIntent)
                    }
                }
            }
        }
    }
}
