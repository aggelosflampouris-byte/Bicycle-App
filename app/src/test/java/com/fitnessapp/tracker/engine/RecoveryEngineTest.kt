package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecoveryEngineTest {

    private lateinit var recoveryEngine: RecoveryEngine

    @Before
    fun setUp() {
        recoveryEngine = RecoveryEngine()
    }

    @Test
    fun testLightWorkoutCalculatesLowTssAndQuickRecovery() {
        val session = WorkoutSessionEntity(
            id = 1L,
            startTime = System.currentTimeMillis() - 3600_000L,
            durationSeconds = 1800L, // 30 mins
            totalDistanceMeters = 10000.0, // 10 km
            avgSpeedKmh = 20.0,
            caloriesBurned = 250.0,
            elevationGainMeters = 30.0,
            wattsPerKg = 1.8,
            activityType = "CYCLING"
        )

        val advice = recoveryEngine.computeRecoveryAdvice(
            targetSession = session,
            recentSessions = emptyList(),
            user = UserEntity(age = 28, weightKg = 72f)
        )

        assertTrue(advice.trainingStressScore in 5.0..100.0)
        assertTrue(advice.recoveryHoursTotal in 6..24)
        assertTrue(advice.recommendedSleepHours in 7.5..8.5)
        assertTrue(advice.hydrationTargetLiters >= 1.5)
    }

    @Test
    fun testHeavyClimbingWorkoutCalculatesHighFatigueAndLongerRecovery() {
        val session = WorkoutSessionEntity(
            id = 2L,
            startTime = System.currentTimeMillis() - 7200_000L,
            durationSeconds = 10800L, // 3 hours
            totalDistanceMeters = 75000.0, // 75 km
            avgSpeedKmh = 27.5,
            caloriesBurned = 1800.0,
            elevationGainMeters = 1200.0,
            wattsPerKg = 3.2,
            activityType = "CYCLING"
        )

        val advice = recoveryEngine.computeRecoveryAdvice(
            targetSession = session,
            recentSessions = emptyList(),
            user = UserEntity(age = 42, weightKg = 75f)
        )

        assertTrue(advice.trainingStressScore > 100.0)
        assertTrue(advice.recoveryHoursTotal >= 24)
        assertTrue(advice.recommendedSleepHours > 8.0)
        assertTrue(advice.actionableTips.any { it.contains("stretching", ignoreCase = true) })
    }

    @Test
    fun testRecoveryPercentageIncreasesAsTimeElapses() {
        val now = System.currentTimeMillis()
        val session = WorkoutSessionEntity(
            id = 3L,
            startTime = now - (20 * 3600_000L), // 20 hours ago
            durationSeconds = 3600L,
            totalDistanceMeters = 25000.0,
            avgSpeedKmh = 25.0,
            caloriesBurned = 600.0,
            elevationGainMeters = 150.0,
            wattsPerKg = 2.4,
            activityType = "CYCLING"
        )

        val adviceImmediate = recoveryEngine.computeRecoveryAdvice(
            targetSession = session,
            recentSessions = emptyList(),
            user = UserEntity(),
            currentTimeMs = session.startTime + 3600_000L // 0 hours after workout
        )

        val adviceLater = recoveryEngine.computeRecoveryAdvice(
            targetSession = session,
            recentSessions = emptyList(),
            user = UserEntity(),
            currentTimeMs = now // 20 hours after workout
        )

        assertTrue(adviceLater.recoveryPercentage >= adviceImmediate.recoveryPercentage)
        assertTrue(adviceLater.remainingRecoveryHours <= adviceImmediate.remainingRecoveryHours)
    }
}
