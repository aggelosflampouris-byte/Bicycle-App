package com.fitnessapp.tracker.domain.usecase

import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ChallengeState(
    val latestChallenge: ChallengeEntity?,
    val completedChallenges: List<ChallengeEntity>,
    val showNewChallengeDialog: Boolean
)

class GetChallengesStateUseCase @Inject constructor(
    private val challengeDao: ChallengeDao
) {
    operator fun invoke(dismissedChallengeIdFlow: Flow<Long?>): Flow<ChallengeState> {
        return combine(
            challengeDao.getLatestChallengeFlow(),
            challengeDao.getCompletedChallengesFlow(),
            dismissedChallengeIdFlow
        ) { latestChallenge, completedChallenges, dismissedId ->
            val showDialog = latestChallenge != null &&
                    latestChallenge.status == ChallengeStatus.PENDING &&
                    latestChallenge.id != dismissedId
            
            ChallengeState(
                latestChallenge = latestChallenge,
                completedChallenges = completedChallenges,
                showNewChallengeDialog = showDialog
            )
        }
    }
}
