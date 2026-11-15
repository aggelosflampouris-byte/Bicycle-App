package com.fitnessapp.tracker.ui.dashboard

import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric

sealed class DashboardUiEvent {
    data class SetActivityType(val type: String) : DashboardUiEvent()
    data class DeleteSession(val sessionId: Long) : DashboardUiEvent()

    // Digital Twin & Environment
    object RefreshEnvironment : DashboardUiEvent()
    data class SubmitWellnessCheckIn(
        val sleepQuality: Int,
        val energyLevel: Int,
        val muscleSoreness: Int,
        val stressLevel: Int,
        val hydrationScore: Int,
        val notes: String = "",
        val type: CheckInType = CheckInType.PRE_WORKOUT
    ) : DashboardUiEvent()

    // Routine
    data class SaveRoutine(
        val interval: RoutineInterval,
        val metric: RoutineMetric,
        val targetValue: Double,
        val autoImprove: Boolean
    ) : DashboardUiEvent()
    object DeleteRoutine : DashboardUiEvent()
}
