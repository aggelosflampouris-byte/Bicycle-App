package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
import com.fitnessapp.tracker.util.DateFormats
import com.fitnessapp.tracker.data.local.entity.ActivityType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

enum class AcwrZone(val label: String, val guidance: String) {
    BUILDING_BASE("Building Base", "Establish steady aerobic habits before ramping volume."),
    DETRAINING("Underloaded", "Training load has dropped; good window for a progressive build."),
    OPTIMAL("Sweet Spot", "Acute and chronic loads are well balanced for safe fitness gains."),
    ELEVATED_RISK("High Strain", "Load spike detected; trim volume or keep pace controlled."),
    OVERTRAINING("Overload Caution", "Significant fatigue spike; prioritize sleep and easy recovery.")
}

enum class RecommendedIntensity(val label: String, val summary: String) {
    REST("Rest & Recharge", "Prioritize sleep, hydration, and light mobility today."),
    RECOVERY_ZONE_1("Zone 1 Active Recovery", "Keep effort gentle to accelerate metabolic recovery."),
    AEROBIC_ZONE_2("Zone 2 Endurance", "Conversational aerobic pacing builds mitochondrial base safely."),
    TEMPO_ZONE_3("Tempo & Rhythm", "Sustained steady-hard efforts suit your current readiness."),
    THRESHOLD_INTERVALS("Peak / Intervals", "High readiness supports fast splits, climbs, or intervals.")
}

data class DigitalTwinState(
    val bodyBattery: Int = 80,
    val readinessScore: Int = 80,
    val acuteLoad: Double = 0.0,
    val chronicLoad: Double = 0.0,
    val acwr: Double = 1.0,
    val acwrZone: AcwrZone = AcwrZone.BUILDING_BASE,
    val environmentStressFactor: Double = 1.0,
    val hydrationRateLitersPerHour: Double = 0.55,
    val recommendedIntensity: RecommendedIntensity = RecommendedIntensity.AEROBIC_ZONE_2,
    val recommendedSport: String = "CYCLING",
    val guidanceText: String = "Balanced readiness. Aim for steady aerobic movement today.",
    val environment: EnvironmentSnapshot = EnvironmentSnapshot(),
    val latestCheckIn: WellnessCheckInEntity? = null,
    val latestSession: WorkoutSessionEntity? = null,
    val todayStrain: Double = 0.0,
    val riskFlags: List<String> = emptyList()
)

data class SimulationOutcome(
    val predictedBatteryAfter: Int,
    val predictedReadinessAfter: Int,
    val estimatedStrain: Double,
    val estimatedCalories: Double,
    val estimatedFluidLossMl: Int,
    val predictedAcwrAfter: Double,
    val predictedAcwrZone: AcwrZone,
    val estimatedRecoveryHours: Int,
    val advisoryNote: String
)

enum class RecoveryStatus(
    val title: String,
    val description: String,
    val colorHex: String
) {
    FULLY_RECOVERED(
        "Fully Primed & Recovered",
        "Your physiological systems are restored. Ideal for progressive efforts or higher-intensity intervals.",
        "#00FF87"
    ),
    ACTIVE_RECOVERY_OPTIMAL(
        "Active Recovery Optimal",
        "Gentle Zone 1–2 aerobic movement will boost circulation and accelerate metabolic clearance.",
        "#03A9F4"
    ),
    MODERATE_FATIGUE(
        "Moderate Fatigue",
        "Muscular and glycogen repair is underway. Keep workouts controlled at steady aerobic pacing.",
        "#FFEB3B"
    ),
    EXHAUSTED_REST_REQUIRED(
        "High Fatigue / Rest Required",
        "High systemic strain and accumulated load. Prioritize sleep, hydration, and rest.",
        "#FF5722"
    )
}

data class RecoveryAdvice(
    val recoveryHoursTotal: Int,
    val remainingRecoveryHours: Int,
    val recoveryPercentage: Int,
    val status: RecoveryStatus,
    val trainingStressScore: Double,
    val recommendedSleepHours: Double,
    val hydrationTargetLiters: Double,
    val actionableTips: List<String>,
    val optimalNextEffortTimestamp: Long
)

/**
 * Closed-loop physiological Digital Twin engine combining:
 * 1) Mechanical load & sensor stability from workouts
 * 2) Outdoor microclimate thermal/wind/UV stress
 * 3) Subjective wellness & RPE user signals
 */
@Singleton
class DigitalTwinEngine @Inject constructor() {
    companion object {
        private const val ONE_DAY_MS = 24L * 3600L * 1000L
        private const val SEVEN_DAYS_MS = 7L * ONE_DAY_MS
        private const val TWENTY_EIGHT_DAYS_MS = 28L * ONE_DAY_MS
    }

    fun calculateEnvironmentStressFactor(env: EnvironmentSnapshot): Double {
        val heatLoad = max(0.0, env.apparentTemperatureC - 22.0) * 0.015
        val coldLoad = max(0.0, 4.0 - env.apparentTemperatureC) * 0.008
        val windLoad = max(0.0, env.windSpeedKmh - 12.0) * 0.005
        val uvLoad = if (env.uvIndex >= 6.0) 0.03 else 0.0
        val rainCaution = if (env.precipitationProbPct >= 60) 0.02 else 0.0

        val raw = 1.0 + heatLoad.coerceAtMost(0.24) + coldLoad.coerceAtMost(0.10) +
            windLoad.coerceAtMost(0.14) + uvLoad + rainCaution
        return raw.coerceIn(0.95, 1.35)
    }

    fun calculateHydrationRateLitersPerHour(
        env: EnvironmentSnapshot,
        weightKg: Float = 75f,
        intensityRpe: Int = 6
    ): Double {
        val safeWeight = if (weightKg > 0f) weightKg.toDouble() else 75.0
        val weightScale = (safeWeight / 75.0).coerceIn(0.75, 1.45)
        val heatExtra = max(0.0, env.apparentTemperatureC - 18.0) * 0.026
        val humidityExtra = max(0.0, env.humidityPct - 60.0) * 0.003
        val effortScale = (0.75 + (intensityRpe.coerceIn(1, 10) / 10.0) * 0.55)
        return ((0.45 + heatExtra + humidityExtra) * weightScale * effortScale).coerceIn(0.35, 1.45)
    }

    fun calculateSessionTwinStrain(
        session: WorkoutSessionEntity,
        userWeightKg: Float = 75f
    ): Double {
        val baseTss = calculateSessionTss(session, userWeightKg)
        val envFactor = session.environmentStressFactor.coerceIn(0.95, 1.40)
        val rpeFactor = session.rpe?.let { (0.75 + (it.coerceIn(1, 10) / 10.0) * 0.5).coerceIn(0.75, 1.25) } ?: 1.0
        val stabilityPenalty = session.gaitStabilityScore?.let { score ->
            when {
                score < 55.0 -> 1.12
                score < 70.0 -> 1.06
                else -> 1.0
            }
        } ?: 1.0

        return (baseTss * envFactor * rpeFactor * stabilityPenalty).coerceIn(5.0, 420.0)
    }

    fun computeTwinState(
        sessions: List<WorkoutSessionEntity>,
        user: UserEntity,
        latestCheckIn: WellnessCheckInEntity?,
        environment: EnvironmentSnapshot,
        preferredActivityType: String = "CYCLING",
        nowMs: Long = System.currentTimeMillis()
    ): DigitalTwinState {
        val validSessions = sessions.filter { it.startTime in 1L..nowMs }

        val sevenDayLoad = validSessions
            .filter { nowMs - it.startTime <= SEVEN_DAYS_MS }
            .sumOf { calculateSessionTwinStrain(it, user.weightKg) }
        val twentyEightDayLoad = validSessions
            .filter { nowMs - it.startTime <= TWENTY_EIGHT_DAYS_MS }
            .sumOf { calculateSessionTwinStrain(it, user.weightKg) }

        val acuteLoad = sevenDayLoad / 7.0
        val chronicLoad = twentyEightDayLoad / 28.0
        val acwr = if (chronicLoad >= 4.0) (acuteLoad / chronicLoad).coerceIn(0.0, 2.5) else 1.0
        val acwrZone = classifyAcwrZone(acwr, chronicLoad)

        val todayStartMs = nowMs - (nowMs % ONE_DAY_MS)
        val todayStrain = validSessions
            .filter { it.startTime >= todayStartMs }
            .sumOf { calculateSessionTwinStrain(it, user.weightKg) }

        // Residual 72-hour fatigue with ~36h exponential recovery time constant
        val residualDrain = validSessions
            .filter { nowMs - it.startTime <= 3L * ONE_DAY_MS }
            .sumOf { s ->
                val sessionEndMs = max(s.startTime, s.endTime)
                val hoursAgo = max(0.0, (nowMs - sessionEndMs) / (1000.0 * 3600.0))
                val strain = calculateSessionTwinStrain(s, user.weightKg)
                strain * 0.42 * exp(-hoursAgo / 36.0)
            }

        val subjectiveAdjustment = computeSubjectiveDelta(latestCheckIn, nowMs)
        val envFactor = calculateEnvironmentStressFactor(environment)
        val envReadinessPenalty = max(0.0, (envFactor - 1.05) * 28.0)

        val rawBattery = (84.0 - residualDrain + subjectiveAdjustment).coerceIn(5.0, 100.0)
        val bodyBattery = rawBattery.roundToInt()

        val acwrPenalty = when (acwrZone) {
            AcwrZone.OVERTRAINING -> 16.0
            AcwrZone.ELEVATED_RISK -> 8.0
            else -> 0.0
        }
        val readinessScore = (rawBattery - envReadinessPenalty - acwrPenalty)
            .roundToInt()
            .coerceIn(5, 100)

        val riskFlags = buildRiskFlags(
            acwrZone = acwrZone,
            environment = environment,
            checkIn = latestCheckIn,
            latestSession = validSessions.firstOrNull()
        )

        val recommendation = determineRecommendedIntensity(readinessScore, acwrZone)
        val recommendedSport = suggestSport(preferredActivityType, readinessScore, environment, latestCheckIn)
        val guidance = buildGuidanceText(recommendation, recommendedSport, acwrZone, environment, riskFlags)
        val hydrationRate = calculateHydrationRateLitersPerHour(environment, user.weightKg)

        return DigitalTwinState(
            bodyBattery = bodyBattery,
            readinessScore = readinessScore,
            acuteLoad = acuteLoad,
            chronicLoad = chronicLoad,
            acwr = acwr,
            acwrZone = acwrZone,
            environmentStressFactor = envFactor,
            hydrationRateLitersPerHour = hydrationRate,
            recommendedIntensity = recommendation,
            recommendedSport = recommendedSport,
            guidanceText = guidance,
            environment = environment,
            latestCheckIn = latestCheckIn,
            latestSession = validSessions.firstOrNull(),
            todayStrain = todayStrain,
            riskFlags = riskFlags
        )
    }

    fun simulateWorkout(
        state: DigitalTwinState,
        user: UserEntity,
        activityType: String,
        durationMinutes: Int,
        targetRpe: Int,
        elevationGainM: Double = 0.0
    ): SimulationOutcome {
        val safeMinutes = durationMinutes.coerceIn(10, 360)
        val safeRpe = targetRpe.coerceIn(1, 10)
        val durationSec = safeMinutes * 60L
        val avgSpeedKmh = representativeSpeedKmh(activityType, safeRpe)
        val distanceMeters = (avgSpeedKmh * 1000.0) * (safeMinutes / 60.0)
        val calories = PhysicsEngine.calculateCalories(user, durationSec, avgSpeedKmh, activityType)
        val wKg = PhysicsEngine.calculateWattsPerKg(avgSpeedKmh, user, activityType)

        val syntheticSession = WorkoutSessionEntity(
            id = Long.MAX_VALUE,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + durationSec * 1000L,
            durationSeconds = durationSec,
            totalDistanceMeters = distanceMeters,
            elevationGainMeters = elevationGainM.coerceAtLeast(0.0),
            avgSpeedKmh = avgSpeedKmh,
            caloriesBurned = calories,
            wattsPerKg = wKg,
            activityType = activityType,
            rpe = safeRpe,
            environmentStressFactor = state.environmentStressFactor
        )

        val estimatedStrain = calculateSessionTwinStrain(syntheticSession, user.weightKg)
        val batteryDrain = (estimatedStrain * 0.42).coerceIn(3.0, 75.0)
        val predictedBattery = (state.bodyBattery - batteryDrain).roundToInt().coerceIn(5, 100)
        val predictedReadiness = (state.readinessScore - batteryDrain * 0.9).roundToInt().coerceIn(5, 100)

        val projectedAcute = state.acuteLoad + (estimatedStrain / 7.0)
        val projectedAcwr = if (state.chronicLoad >= 4.0) {
            (projectedAcute / state.chronicLoad).coerceIn(0.0, 2.8)
        } else {
            state.acwr
        }
        val projectedZone = classifyAcwrZone(projectedAcwr, state.chronicLoad)

        val hydrationRate = calculateHydrationRateLitersPerHour(state.environment, user.weightKg, safeRpe)
        val fluidLossMl = (hydrationRate * (safeMinutes / 60.0) * 1000.0).roundToInt()

        val advice = computeRecoveryAdvice(
            targetSession = syntheticSession,
            recentSessions = state.latestSession?.let { listOf(it) } ?: emptyList(),
            user = user,
            latestCheckIn = state.latestCheckIn,
            twinState = state,
            currentTimeMs = syntheticSession.endTime
        )

        val advisory = when {
            predictedBattery < 25 -> "Heavy depletion predicted. Consider shortening duration or lowering RPE to keep recovery under 24h."
            projectedZone == AcwrZone.OVERTRAINING -> "Load spike pushes ACWR above 1.5. Swap to gentle Zone 1–2 recovery or take a rest day."
            state.environment.apparentTemperatureC >= 28.0 -> "Thermal load adds ${( (state.environmentStressFactor - 1.0) * 100 ).roundToInt()}% strain; drink ~$fluidLossMl mL."
            else -> "Sustainable session for your Twin. Expected recovery: ~${advice.recoveryHoursTotal}h."
        }

        return SimulationOutcome(
            predictedBatteryAfter = predictedBattery,
            predictedReadinessAfter = predictedReadiness,
            estimatedStrain = estimatedStrain,
            estimatedCalories = calories,
            estimatedFluidLossMl = fluidLossMl,
            predictedAcwrAfter = projectedAcwr,
            predictedAcwrZone = projectedZone,
            estimatedRecoveryHours = advice.recoveryHoursTotal,
            advisoryNote = advisory
        )
    }

    fun toSnapshotEntity(state: DigitalTwinState, nowMs: Long = System.currentTimeMillis()): TwinSnapshotEntity {
        val dateKey = DateFormats.formatIsoDate(nowMs)
        return TwinSnapshotEntity(
            dateKey = dateKey,
            updatedAtMs = nowMs,
            readinessScore = state.readinessScore,
            bodyBattery = state.bodyBattery,
            acuteLoad = state.acuteLoad,
            chronicLoad = state.chronicLoad,
            acwr = state.acwr,
            strainScore = state.todayStrain,
            recommendedIntensity = state.recommendedIntensity.name,
            weatherTempC = state.environment.temperatureC,
            windSpeedKmh = state.environment.windSpeedKmh,
            humidityPct = state.environment.humidityPct,
            uvIndex = state.environment.uvIndex
        )
    }

    /**
     * Estimates base mechanical Training Stress Score (TSS) before RPE, gait stability,
     * and microclimate modifiers are applied by [calculateSessionTwinStrain].
     */
    fun calculateSessionTss(session: WorkoutSessionEntity, userWeightKg: Float = 75f): Double {
        val durationHours = max(1.0, session.durationSeconds.toDouble()) / 3600.0
        val baselineSpeed = ActivityType.fromString(session.activityType).defaultAvgSpeedKmh
        val speedRatio = (session.avgSpeedKmh / baselineSpeed).coerceIn(0.5, 2.5)
        val elevationFactor = (session.elevationGainMeters / 100.0) * 10.0
        val calorieFactor = (session.caloriesBurned / 500.0) * 12.0
        val normalizedWKg = PhysicsEngine.resolveWattsPerKg(session.wattsPerKg, userWeightKg)
        val powerFactor = if (normalizedWKg > 0) (normalizedWKg / 2.5) * 15.0 else 0.0

        val baseTss = (durationHours * 45.0 * speedRatio * speedRatio) + elevationFactor + calorieFactor + powerFactor
        return baseTss.coerceIn(5.0, 350.0)
    }

    /**
     * Computes unified Twin-aware post-workout recovery guidance combining session strain
     * (RPE, environment stress, stability drift), 7-day decayed fatigue, ACWR overload state,
     * subjective check-ins, and thermal fluid demand.
     */
    fun computeRecoveryAdvice(
        targetSession: WorkoutSessionEntity,
        recentSessions: List<WorkoutSessionEntity> = emptyList(),
        user: UserEntity = UserEntity(),
        latestCheckIn: WellnessCheckInEntity? = null,
        twinState: DigitalTwinState? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RecoveryAdvice {
        val sessionStrain = calculateSessionTwinStrain(targetSession, user.weightKg)

        var cumulativeFatigue = sessionStrain
        recentSessions.forEach { prev ->
            if (prev.id != targetSession.id && prev.startTime < targetSession.startTime) {
                val elapsedHours = max(0.0, (targetSession.startTime - prev.startTime) / (1000.0 * 3600.0))
                val prevStrain = calculateSessionTwinStrain(prev, user.weightKg)
                val decayedLoad = prevStrain * kotlin.math.exp(-elapsedHours / 48.0)
                cumulativeFatigue += decayedLoad
            }
        }

        val ageMultiplier = if (user.age > 30) {
            1.0 + ((user.age - 30) * 0.01).coerceAtMost(0.4)
        } else 1.0

        val acwrMultiplier = when (twinState?.acwrZone) {
            AcwrZone.OVERTRAINING -> 1.20
            AcwrZone.ELEVATED_RISK -> 1.10
            else -> 1.0
        }

        val wellnessMultiplier = if (latestCheckIn != null) {
            val poorSleepPenalty = if (latestCheckIn.sleepQuality <= 2) 0.12 else 0.0
            val sorenessPenalty = if (latestCheckIn.muscleSoreness >= 4) 0.10 else 0.0
            val freshCredit = if (latestCheckIn.sleepQuality >= 4 && latestCheckIn.muscleSoreness <= 2) -0.08 else 0.0
            (1.0 + poorSleepPenalty + sorenessPenalty + freshCredit).coerceIn(0.88, 1.25)
        } else 1.0

        val rawHours = (cumulativeFatigue * 0.28 * ageMultiplier * acwrMultiplier * wellnessMultiplier).coerceIn(6.0, 72.0)
        val totalRecoveryHours = rawHours.roundToInt()

        val sessionEndTime = targetSession.startTime + (targetSession.durationSeconds * 1000L)
        val hoursElapsedSinceWorkout = max(0.0, (currentTimeMs - sessionEndTime) / (1000.0 * 3600.0))

        val remainingHours = max(0.0, totalRecoveryHours - hoursElapsedSinceWorkout).roundToInt()
        val recoveryPct = if (totalRecoveryHours > 0) {
            (((totalRecoveryHours - remainingHours).toDouble() / totalRecoveryHours) * 100.0).roundToInt().coerceIn(0, 100)
        } else 100

        var status = when {
            remainingHours <= 2 -> RecoveryStatus.FULLY_RECOVERED
            remainingHours <= 12 -> RecoveryStatus.ACTIVE_RECOVERY_OPTIMAL
            remainingHours <= 28 -> RecoveryStatus.MODERATE_FATIGUE
            else -> RecoveryStatus.EXHAUSTED_REST_REQUIRED
        }
        if (twinState?.acwrZone == AcwrZone.OVERTRAINING && status == RecoveryStatus.FULLY_RECOVERED) {
            status = RecoveryStatus.MODERATE_FATIGUE
        } else if (twinState?.acwrZone == AcwrZone.ELEVATED_RISK && status == RecoveryStatus.FULLY_RECOVERED) {
            status = RecoveryStatus.ACTIVE_RECOVERY_OPTIMAL
        }

        val extraSleepHours = (totalRecoveryHours / 24.0) * 0.75 +
            if ((targetSession.rpe ?: 0) >= 8 || twinState?.acwrZone == AcwrZone.OVERTRAINING) 0.25 else 0.0
        val recommendedSleep = (7.5 + extraSleepHours).coerceIn(7.5, 9.5)

        val envBonusLiters = max(0.0, targetSession.environmentStressFactor - 1.0) * 1.6
        val hydrationLiters = (
            1.5 +
                (targetSession.caloriesBurned / 1000.0) * 0.8 +
                (targetSession.elevationGainMeters / 1000.0) * 0.5 +
                envBonusLiters
            ).coerceIn(1.8, 4.8)

        val tips = mutableListOf<String>()
        tips.add("Target %.1f hours of uninterrupted sleep tonight to support nervous system recovery.".format(recommendedSleep))
        tips.add("Drink %.1f L of water + electrolytes to restore plasma and sodium balance.".format(hydrationLiters))

        if (targetSession.environmentStressFactor > 1.08) {
            tips.add("Elevated outdoor weather stress (${"x%.2f".format(targetSession.environmentStressFactor)}): cool down gradually and top up electrolytes early.")
        }
        if ((targetSession.rpe ?: 0) >= 8) {
            tips.add("High perceived exertion (RPE ${targetSession.rpe}/10): prioritize mobility work and extra recovery before your next hard block.")
        }
        if ((targetSession.gaitStabilityScore ?: 100.0) < 75.0) {
            tips.add("Noticeable form/stability drift recorded late in this session — include core stability and dynamic warmups.")
        }
        if (targetSession.elevationGainMeters > 300) {
            tips.add("Significant climbing load: perform 10 mins of gentle mobility for quads, calves, and lower back.")
        }
        if (targetSession.caloriesBurned > 600) {
            tips.add("High energy expenditure: refuel with complex carbohydrates and 25–30g quality protein.")
        }
        if (twinState?.acwrZone == AcwrZone.OVERTRAINING || remainingHours > 24) {
            tips.add("Keep your next session to gentle Zone 1–2 aerobic movement or complete rest until ACWR & soreness settle.")
        } else {
            tips.add("Light Zone 1–2 aerobic recovery will boost circulation without compounding fatigue.")
        }

        val optimalNextTimestamp = sessionEndTime + (totalRecoveryHours * 3600L * 1000L)

        return RecoveryAdvice(
            recoveryHoursTotal = totalRecoveryHours,
            remainingRecoveryHours = remainingHours,
            recoveryPercentage = recoveryPct,
            status = status,
            trainingStressScore = sessionStrain,
            recommendedSleepHours = recommendedSleep,
            hydrationTargetLiters = hydrationLiters,
            actionableTips = tips,
            optimalNextEffortTimestamp = optimalNextTimestamp
        )
    }

    private fun computeSubjectiveDelta(checkIn: WellnessCheckInEntity?, nowMs: Long): Double {
        if (checkIn == null) return 0.0
        val ageHours = max(0.0, (nowMs - checkIn.timestampMs) / (1000.0 * 3600.0))
        val freshnessWeight = exp(-ageHours / 24.0)
        val rawDelta = ((checkIn.sleepQuality - 3) * 3.5) +
            ((checkIn.energyLevel - 3) * 4.0) -
            ((checkIn.muscleSoreness - 2) * 3.5) -
            ((checkIn.stressLevel - 2) * 2.5) +
            ((checkIn.hydrationScore - 3) * 2.0)
        return (rawDelta * freshnessWeight).coerceIn(-25.0, 18.0)
    }

    private fun classifyAcwrZone(acwr: Double, chronicLoad: Double): AcwrZone {
        if (chronicLoad < 8.0) return AcwrZone.BUILDING_BASE
        return when {
            acwr < 0.8 -> AcwrZone.DETRAINING
            acwr <= 1.3 -> AcwrZone.OPTIMAL
            acwr <= 1.5 -> AcwrZone.ELEVATED_RISK
            else -> AcwrZone.OVERTRAINING
        }
    }

    private fun determineRecommendedIntensity(readiness: Int, acwrZone: AcwrZone): RecommendedIntensity {
        return when {
            readiness < 30 || acwrZone == AcwrZone.OVERTRAINING -> RecommendedIntensity.REST
            readiness < 50 -> RecommendedIntensity.RECOVERY_ZONE_1
            readiness < 70 || acwrZone == AcwrZone.ELEVATED_RISK -> RecommendedIntensity.AEROBIC_ZONE_2
            readiness < 85 -> RecommendedIntensity.TEMPO_ZONE_3
            else -> RecommendedIntensity.THRESHOLD_INTERVALS
        }
    }

    private fun suggestSport(
        preferred: String,
        readiness: Int,
        env: EnvironmentSnapshot,
        checkIn: WellnessCheckInEntity?
    ): String {
        if (readiness < 40 || (checkIn?.muscleSoreness ?: 1) >= 4) return "WALKING"
        if (env.windSpeedKmh >= 28.0 && preferred == "CYCLING") return "JOGGING"
        return preferred
    }

    private fun buildRiskFlags(
        acwrZone: AcwrZone,
        environment: EnvironmentSnapshot,
        checkIn: WellnessCheckInEntity?,
        latestSession: WorkoutSessionEntity?
    ): List<String> {
        val flags = mutableListOf<String>()
        if (acwrZone == AcwrZone.OVERTRAINING || acwrZone == AcwrZone.ELEVATED_RISK) {
            flags += "${acwrZone.label}: ease intensity to avoid overreaching"
        }
        if (environment.apparentTemperatureC >= 28.0) {
            flags += "Thermal load (${"%.0f".format(environment.apparentTemperatureC)}°C feels-like)"
        }
        if (environment.windSpeedKmh >= 24.0) {
            flags += "High outdoor wind (${"%.0f".format(environment.windSpeedKmh)} km/h)"
        }
        if (environment.uvIndex >= 6.5) {
            flags += "Elevated UV (${"%.1f".format(environment.uvIndex)})"
        }
        if ((checkIn?.muscleSoreness ?: 1) >= 4) {
            flags += "Elevated leg/muscle soreness reported"
        }
        if ((latestSession?.gaitStabilityScore ?: 100.0) < 65.0) {
            flags += "Recent form/gait stability drift detected"
        }
        return flags
    }

    private fun buildGuidanceText(
        intensity: RecommendedIntensity,
        sport: String,
        acwrZone: AcwrZone,
        env: EnvironmentSnapshot,
        flags: List<String>
    ): String {
        val sportLabel = ActivityType.fromString(sport).displayTitle
        val base = "${intensity.label} ($sportLabel): ${intensity.summary}"
        val envNote = env.bestWindowLabel?.let { " $it." } ?: ""
        val riskNote = if (flags.isNotEmpty()) " Watch: ${flags.first()}." else ""
        return base + envNote + riskNote
    }

    private fun representativeSpeedKmh(activityType: String, rpe: Int): Double {
        val fraction = ((rpe - 1).coerceIn(0, 9)) / 9.0
        return when (activityType) {
            "WALKING" -> 4.0 + fraction * 2.5
            "JOGGING" -> 7.5 + fraction * 5.5
            else -> 16.0 + fraction * 17.0
        }
    }
}

