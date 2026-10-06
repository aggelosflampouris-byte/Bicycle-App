package com.fitnessapp.tracker.domain.usecase

import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class DashboardStats(
    val sessions: List<WorkoutSessionEntity>,
    val totalDistanceKm: Double,
    val avgDistanceKm: Double,
    val totalCalories: Double
)

class GetDashboardStatsUseCase @Inject constructor(
    private val sessionDao: WorkoutSessionDao
) {
    operator fun invoke(activityType: String): Flow<DashboardStats> {
        return sessionDao.getAllSessionsFlow().map { allSessions ->
            val sessions = allSessions.filter { it.activityType == activityType }
            val totalDist = sessions.sumOf { it.totalDistanceMeters } / 1000.0
            val avgDist = if (sessions.isNotEmpty()) totalDist / sessions.size else 0.0
            val totalCals = sessions.sumOf { it.caloriesBurned }
            
            DashboardStats(
                sessions = sessions,
                totalDistanceKm = totalDist,
                avgDistanceKm = avgDist,
                totalCalories = totalCals
            )
        }
    }
}
