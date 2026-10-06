package com.fitnessapp.tracker.domain.usecase

import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.RecoveryAdvice
import com.fitnessapp.tracker.engine.RecoveryEngine
import javax.inject.Inject

class GetRecoveryAdviceUseCase @Inject constructor(
    private val recoveryEngine: RecoveryEngine
) {
    operator fun invoke(sessions: List<WorkoutSessionEntity>, user: UserEntity): RecoveryAdvice? {
        if (sessions.isEmpty()) return null
        
        return recoveryEngine.computeRecoveryAdvice(
            targetSession = sessions.first(), // Assuming sessions are sorted by date descending
            recentSessions = sessions,
            user = user
        )
    }
}
