package com.fitnessapp.tracker.ui.dashboard

import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric

sealed class DashboardUiEvent {
    data class SetActivityType(val type: String) : DashboardUiEvent()
    data class DeleteSession(val sessionId: Long) : DashboardUiEvent()
    
    // Challenges
    data class RespondToChallenge(val challenge: ChallengeEntity, val accept: Boolean) : DashboardUiEvent()
    data class CancelChallenge(val challenge: ChallengeEntity) : DashboardUiEvent()
    
    // Training Plan
    data class GenerateTrainingPlan(val goalPrompt: String) : DashboardUiEvent()
    data class ToggleDailyPlanCompleted(val day: String) : DashboardUiEvent()
    
    // Routine
    data class SaveRoutine(
        val interval: RoutineInterval,
        val metric: RoutineMetric,
        val targetValue: Double,
        val autoImprove: Boolean
    ) : DashboardUiEvent()
    object DeleteRoutine : DashboardUiEvent()
}
