package com.example.smartcyclingtracker.engine

import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.ActivityType
import com.example.smartcyclingtracker.data.local.entity.ChallengeEntity
import com.example.smartcyclingtracker.data.local.entity.ChallengeMetric
import com.example.smartcyclingtracker.data.local.entity.ChallengeStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ChallengeGenerator @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao
) {
    suspend fun generateNewChallenge(): ChallengeEntity {
        val activities = ActivityType.entries.toTypedArray()
        val randomActivity = activities[Random.nextInt(activities.size)]
        
        val metrics = ChallengeMetric.entries.toTypedArray()
        val randomMetric = metrics[Random.nextInt(metrics.size)]
        
        val periods = arrayOf("DAY", "WEEK", "MONTH")
        val randomPeriod = periods[Random.nextInt(periods.size)]
        
        // Fetch last 10 sessions of this activity type to establish a baseline
        val sessions = workoutSessionDao.getRecentSessionsByType(randomActivity.name, limit = 10)
        
        var targetValue = 0.0
        
        if (sessions.isNotEmpty()) {
            when (randomMetric) {
                ChallengeMetric.DISTANCE -> {
                    val avgDistance = sessions.map { it.totalDistanceMeters }.average()
                    targetValue = avgDistance * getPeriodMultiplier(randomPeriod) * 1.1 // 10% harder
                }
                ChallengeMetric.SPEED -> {
                    val maxSpeed = sessions.maxOf { it.avgSpeedKmh }
                    targetValue = maxSpeed * 1.05 // 5% faster than max
                }
                ChallengeMetric.CALORIES -> {
                    val avgCals = sessions.map { it.caloriesBurned }.average()
                    targetValue = avgCals * getPeriodMultiplier(randomPeriod) * 1.1
                }
            }
        } else {
            // Default easy baselines
            when (randomMetric) {
                ChallengeMetric.DISTANCE -> {
                    targetValue = when (randomActivity) {
                        ActivityType.CYCLING -> 5000.0 // 5km
                        ActivityType.WALKING -> 2000.0 // 2km
                        ActivityType.JOGGING -> 3000.0 // 3km
                    } * getPeriodMultiplier(randomPeriod)
                }
                ChallengeMetric.SPEED -> {
                    targetValue = when (randomActivity) {
                        ActivityType.CYCLING -> 15.0 // 15 km/h
                        ActivityType.WALKING -> 4.0 // 4 km/h
                        ActivityType.JOGGING -> 8.0 // 8 km/h
                    }
                }
                ChallengeMetric.CALORIES -> {
                    targetValue = 200.0 * getPeriodMultiplier(randomPeriod)
                }
            }
        }
        
        // Round nicely
        targetValue = Math.round(targetValue * 10.0) / 10.0

        return ChallengeEntity(
            activityType = randomActivity.name,
            metric = randomMetric.name,
            targetValue = targetValue,
            period = randomPeriod,
            status = ChallengeStatus.PENDING.name
        )
    }
    
    private fun getPeriodMultiplier(period: String): Double {
        return when (period) {
            "DAY" -> 1.0
            "WEEK" -> 3.0 // Assume 3 workouts a week
            "MONTH" -> 10.0 // Assume 10 workouts a month
            else -> 1.0
        }
    }
}
