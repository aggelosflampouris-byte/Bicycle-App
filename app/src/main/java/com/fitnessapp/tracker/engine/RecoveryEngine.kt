package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

enum class RecoveryStatus(
    val title: String,
    val description: String,
    val colorHex: String
) {
    FULLY_RECOVERED(
        "Fully Primed & Recovered",
        "Your physiological systems are fully restored. Ideal for maximum effort, PR attempts, or high-intensity intervals.",
        "#00FF87" // ElectricGreen
    ),
    ACTIVE_RECOVERY_OPTIMAL(
        "Active Recovery Optimal",
        "Light aerobic movement will boost blood circulation and accelerate metabolic clearance.",
        "#03A9F4" // LightBlue
    ),
    MODERATE_FATIGUE(
        "Moderate Fatigue",
        "Muscular and glycogen repair is underway. Keep workouts at steady Zone 2 aerobic pacing.",
        "#FFEB3B" // Yellow
    ),
    EXHAUSTED_REST_REQUIRED(
        "High Fatigue / Rest Required",
        "High systemic stress and muscle breakdown. Prioritize deep sleep, nutrition, and rest.",
        "#FF5722" // OrangeRed
    )
}

data class RecoveryAdvice(
    val recoveryHoursTotal: Int,
    val remainingRecoveryHours: Int,
    val recoveryPercentage: Int, // 0 - 100
    val status: RecoveryStatus,
    val trainingStressScore: Double,
    val recommendedSleepHours: Double,
    val hydrationTargetLiters: Double,
    val actionableTips: List<String>,
    val optimalNextEffortTimestamp: Long
)

@Singleton
class RecoveryEngine @Inject constructor() {

    /**
     * Calculates the estimated Training Stress Score (TSS) for a single workout session.
     */
    fun calculateSessionTss(session: WorkoutSessionEntity): Double {
        val durationHours = max(1.0, session.durationSeconds.toDouble()) / 3600.0
        val baselineSpeed = when (session.activityType) {
            "WALKING" -> 4.8
            "JOGGING" -> 9.5
            else -> 23.0
        }
        val speedRatio = (session.avgSpeedKmh / baselineSpeed).coerceIn(0.5, 2.5)
        val elevationFactor = (session.elevationGainMeters / 100.0) * 10.0
        val calorieFactor = (session.caloriesBurned / 500.0) * 12.0
        val powerFactor = if (session.wattsPerKg > 0) (session.wattsPerKg / 2.5) * 15.0 else 0.0

        val baseTss = (durationHours * 45.0 * speedRatio * speedRatio) + elevationFactor + calorieFactor + powerFactor
        return baseTss.coerceIn(5.0, 350.0)
    }

    /**
     * Computes comprehensive recovery advice based on the workout session, recent history, and user biometrics.
     */
    fun computeRecoveryAdvice(
        targetSession: WorkoutSessionEntity,
        recentSessions: List<WorkoutSessionEntity> = emptyList(),
        user: UserEntity = UserEntity(),
        currentTimeMs: Long = System.currentTimeMillis()
    ): RecoveryAdvice {
        val sessionTss = calculateSessionTss(targetSession)

        // Calculate 7-day cumulative fatigue with exponential decay
        var cumulativeFatigue = sessionTss
        recentSessions.forEach { prev ->
            if (prev.id != targetSession.id && prev.startTime < targetSession.startTime) {
                val elapsedHours = max(0.0, (targetSession.startTime - prev.startTime) / (1000.0 * 3600.0))
                val prevTss = calculateSessionTss(prev)
                // Half-life decay of ~48 hours
                val decayedLoad = prevTss * exp(-elapsedHours / 48.0)
                cumulativeFatigue += decayedLoad
            }
        }

        // Age factor multiplier (older athletes require slight recovery scaling)
        val ageMultiplier = if (user.age > 30) {
            1.0 + ((user.age - 30) * 0.01).coerceAtMost(0.4)
        } else 1.0

        // Total recovery hours
        val rawHours = (cumulativeFatigue * 0.28 * ageMultiplier).coerceIn(6.0, 72.0)
        val totalRecoveryHours = rawHours.roundToInt()

        // Time elapsed since session ended
        val sessionEndTime = targetSession.startTime + (targetSession.durationSeconds * 1000L)
        val hoursElapsedSinceWorkout = max(0.0, (currentTimeMs - sessionEndTime) / (1000.0 * 3600.0))

        val remainingHours = max(0.0, totalRecoveryHours - hoursElapsedSinceWorkout).roundToInt()
        val recoveryPct = if (totalRecoveryHours > 0) {
            (((totalRecoveryHours - remainingHours).toDouble() / totalRecoveryHours) * 100.0).roundToInt().coerceIn(0, 100)
        } else 100

        // Status classification
        val status = when {
            remainingHours <= 2 -> RecoveryStatus.FULLY_RECOVERED
            remainingHours <= 12 -> RecoveryStatus.ACTIVE_RECOVERY_OPTIMAL
            remainingHours <= 28 -> RecoveryStatus.MODERATE_FATIGUE
            else -> RecoveryStatus.EXHAUSTED_REST_REQUIRED
        }

        // Recommended sleep (base 7.5h + extra recovery load)
        val extraSleepHours = (totalRecoveryHours / 24.0) * 0.75
        val recommendedSleep = (7.5 + extraSleepHours).coerceIn(7.5, 9.5)

        // Hydration replenishment target
        val hydrationLiters = (1.5 + (targetSession.caloriesBurned / 1000.0) * 0.8 + (targetSession.elevationGainMeters / 1000.0) * 0.5).coerceIn(1.8, 4.5)

        // Actionable tips tailored to workout intensity
        val tips = mutableListOf<String>()
        tips.add("Target %.1f hours of uninterrupted sleep tonight for optimal hormone release.".format(recommendedSleep))
        tips.add("Drink %.1f L of water + electrolytes to restore fluid balance.".format(hydrationLiters))

        if (targetSession.elevationGainMeters > 300) {
            tips.add("High climbing load: Perform 10 mins of gentle stretching for quads, calves, and lower back.")
        }
        if (targetSession.caloriesBurned > 600) {
            tips.add("High energy expenditure: Refuel with complex carbohydrates and 25–30g quality protein.")
        }
        if (remainingHours > 24) {
            tips.add("Limit physical exertion today to Zone 1 light spinning or easy walking.")
        } else {
            tips.add("Light active recovery will boost circulation and clear lactic buildup.")
        }

        val optimalNextTimestamp = sessionEndTime + (totalRecoveryHours * 3600L * 1000L)

        return RecoveryAdvice(
            recoveryHoursTotal = totalRecoveryHours,
            remainingRecoveryHours = remainingHours,
            recoveryPercentage = recoveryPct,
            status = status,
            trainingStressScore = sessionTss,
            recommendedSleepHours = recommendedSleep,
            hydrationTargetLiters = hydrationLiters,
            actionableTips = tips,
            optimalNextEffortTimestamp = optimalNextTimestamp
        )
    }
}
