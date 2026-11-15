package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
import org.junit.Assert.*
import org.junit.Test

class DigitalTwinEngineTest {

    private val engine = DigitalTwinEngine()
    private val defaultUser = UserEntity(age = 30, weightKg = 75f, heightCm = 178f, gender = "male")

    @Test
    fun `environment stress factor rises monotonically in heat and strong wind`() {
        val mild = EnvironmentSnapshot(apparentTemperatureC = 17.0, humidityPct = 50.0, windSpeedKmh = 6.0, uvIndex = 2.0)
        val hotWindy = EnvironmentSnapshot(apparentTemperatureC = 32.0, humidityPct = 75.0, windSpeedKmh = 30.0, uvIndex = 8.0)

        val mildFactor = engine.calculateEnvironmentStressFactor(mild)
        val hotFactor = engine.calculateEnvironmentStressFactor(hotWindy)

        assertEquals(1.0, mildFactor, 0.02)
        assertTrue("Hot/windy stress should exceed mild weather", hotFactor > mildFactor)
        assertTrue(hotFactor <= 1.35)
    }

    @Test
    fun `twin readiness reacts to recent heavy session and restorative check-in`() {
        val now = 1_700_100_000_000L
        val heavySession = WorkoutSessionEntity(
            id = 1L,
            startTime = now - 3 * 3600_000L,
            endTime = now - 1 * 3600_000L,
            durationSeconds = 7200L,
            totalDistanceMeters = 55_000.0,
            elevationGainMeters = 900.0,
            avgSpeedKmh = 27.5,
            caloriesBurned = 1400.0,
            wattsPerKg = 2.8,
            activityType = "CYCLING",
            rpe = 9,
            environmentStressFactor = 1.18
        )
        val poorCheckIn = WellnessCheckInEntity(
            timestampMs = now - 1800_000L,
            type = CheckInType.DAILY,
            sleepQuality = 2,
            energyLevel = 2,
            muscleSoreness = 4,
            stressLevel = 4,
            hydrationScore = 2
        )
        val freshCheckIn = WellnessCheckInEntity(
            timestampMs = now - 1800_000L,
            type = CheckInType.DAILY,
            sleepQuality = 5,
            energyLevel = 5,
            muscleSoreness = 1,
            stressLevel = 1,
            hydrationScore = 5
        )
        val mildWeather = EnvironmentSnapshot(apparentTemperatureC = 19.0, windSpeedKmh = 8.0)

        val fatiguedState = engine.computeTwinState(listOf(heavySession), defaultUser, poorCheckIn, mildWeather, nowMs = now)
        val restedState = engine.computeTwinState(emptyList(), defaultUser, freshCheckIn, mildWeather, nowMs = now)

        assertTrue("Fatigued battery (${fatiguedState.bodyBattery}) should be below rested (${restedState.bodyBattery})",
            fatiguedState.bodyBattery < restedState.bodyBattery)
        assertTrue(fatiguedState.riskFlags.any { it.contains("soreness", ignoreCase = true) })
        assertTrue(restedState.readinessScore >= 85)
    }

    @Test
    fun `what-if simulator scales depletion with duration and RPE within safe bounds`() {
        val rested = engine.computeTwinState(
            sessions = emptyList(),
            user = defaultUser,
            latestCheckIn = null,
            environment = EnvironmentSnapshot(apparentTemperatureC = 26.0, windSpeedKmh = 14.0)
        )

        val easyShort = engine.simulateWorkout(rested, defaultUser, "JOGGING", durationMinutes = 25, targetRpe = 3)
        val hardLong = engine.simulateWorkout(rested, defaultUser, "JOGGING", durationMinutes = 90, targetRpe = 9)

        assertTrue(easyShort.predictedBatteryAfter in 5..100)
        assertTrue(hardLong.predictedBatteryAfter in 5..100)
        assertTrue("Long intense workout should deplete battery more",
            hardLong.predictedBatteryAfter < easyShort.predictedBatteryAfter)
        assertTrue("Long intense workout should require more fluid replacement",
            hardLong.estimatedFluidLossMl > easyShort.estimatedFluidLossMl)
        assertTrue(hardLong.estimatedRecoveryHours >= easyShort.estimatedRecoveryHours)
    }

    @Test
    fun `session twin strain reflects rpe, stability drift and serializes snapshot cleanly`() {
        val baseSession = WorkoutSessionEntity(
            id = 42L,
            startTime = 1_700_050_000_000L,
            endTime = 1_700_053_600_000L,
            durationSeconds = 3600L,
            totalDistanceMeters = 10_000.0,
            avgSpeedKmh = 10.0,
            caloriesBurned = 650.0,
            activityType = "JOGGING",
            rpe = 4,
            gaitStabilityScore = 90.0,
            environmentStressFactor = 1.0
        )
        val highStrainSession = baseSession.copy(
            rpe = 9,
            gaitStabilityScore = 50.0,
            environmentStressFactor = 1.25
        )

        val lowStrain = engine.calculateSessionTwinStrain(baseSession, defaultUser.weightKg)
        val highStrain = engine.calculateSessionTwinStrain(highStrainSession, defaultUser.weightKg)

        assertTrue("High RPE + instability + heat should increase Twin Strain ($highStrain vs $lowStrain)",
            highStrain > lowStrain * 1.4)

        val state = engine.computeTwinState(
            sessions = listOf(highStrainSession),
            user = defaultUser,
            latestCheckIn = null,
            environment = EnvironmentSnapshot(temperatureC = 29.0, windSpeedKmh = 18.0, uvIndex = 7.0),
            nowMs = 1_700_057_200_000L
        )
        val snapshot = engine.toSnapshotEntity(state, nowMs = 1_700_057_200_000L)

        assertEquals(state.readinessScore, snapshot.readinessScore)
        assertEquals(state.bodyBattery, snapshot.bodyBattery)
        assertEquals(29.0, snapshot.weatherTempC ?: 0.0, 0.01)
    }

    @Test
    fun `computeRecoveryAdvice scales with session strain, twin state, and stays sport neutral`() {
        val easyWalk = WorkoutSessionEntity(
            id = 10L,
            startTime = 1_700_050_000_000L,
            endTime = 1_700_051_800_000L,
            durationSeconds = 1800L,
            totalDistanceMeters = 2_500.0,
            avgSpeedKmh = 5.0,
            caloriesBurned = 150.0,
            activityType = "WALKING",
            rpe = 2,
            environmentStressFactor = 1.0
        )
        val hardHotJog = WorkoutSessionEntity(
            id = 11L,
            startTime = 1_700_050_000_000L,
            endTime = 1_700_055_400_000L,
            durationSeconds = 5400L,
            totalDistanceMeters = 18_000.0,
            avgSpeedKmh = 12.0,
            caloriesBurned = 1100.0,
            activityType = "JOGGING",
            rpe = 9,
            gaitStabilityScore = 52.0,
            weatherTempC = 32.0,
            environmentStressFactor = 1.3
        )
        val stressedState = engine.computeTwinState(
            sessions = listOf(hardHotJog),
            user = defaultUser,
            latestCheckIn = null,
            environment = EnvironmentSnapshot(temperatureC = 32.0, windSpeedKmh = 20.0, uvIndex = 8.0),
            nowMs = 1_700_056_000_000L
        )

        val easyRecovery = engine.computeRecoveryAdvice(
            targetSession = easyWalk,
            recentSessions = listOf(easyWalk),
            user = defaultUser,
            currentTimeMs = 1_700_052_000_000L
        )
        val hardRecovery = engine.computeRecoveryAdvice(
            targetSession = hardHotJog,
            recentSessions = listOf(hardHotJog),
            user = defaultUser,
            twinState = stressedState,
            currentTimeMs = 1_700_056_000_000L
        )

        assertTrue(
            "Hard/hot high-RPE session should require more recovery hours (${hardRecovery.recoveryHoursTotal} vs ${easyRecovery.recoveryHoursTotal})",
            hardRecovery.recoveryHoursTotal > easyRecovery.recoveryHoursTotal
        )
        assertTrue(
            "Hard/hot session should require higher hydration target (${hardRecovery.hydrationTargetLiters} vs ${easyRecovery.hydrationTargetLiters})",
            hardRecovery.hydrationTargetLiters > easyRecovery.hydrationTargetLiters
        )
        assertTrue(hardRecovery.actionableTips.isNotEmpty())
        assertTrue(
            "Recovery tips for walking/jogging should remain sport-neutral",
            hardRecovery.actionableTips.none { tip ->
                tip.contains("cycling", ignoreCase = true) || tip.contains("bicycle", ignoreCase = true)
            }
        )
    }
}

