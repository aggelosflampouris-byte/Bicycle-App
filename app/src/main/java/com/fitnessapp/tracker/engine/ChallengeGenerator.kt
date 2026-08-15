package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.ActivityType
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeMetric
import com.fitnessapp.tracker.data.local.entity.ChallengePeriod
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
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
        
        val periods = ChallengePeriod.entries.toTypedArray()
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
            metric = randomMetric,
            targetValue = targetValue,
            period = randomPeriod,
            status = ChallengeStatus.PENDING
        )
    }
    
    private fun getPeriodMultiplier(period: ChallengePeriod): Double {
        return when (period) {
            ChallengePeriod.DAY   -> 1.0
            ChallengePeriod.WEEK  -> 3.0  // Assume 3 workouts a week
            ChallengePeriod.MONTH -> 10.0 // Assume 10 workouts a month
        }
    }
}
