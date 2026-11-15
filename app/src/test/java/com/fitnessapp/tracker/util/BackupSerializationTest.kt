package com.fitnessapp.tracker.util

import com.fitnessapp.tracker.data.local.entity.*
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class BackupSerializationTest {

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    @Test
    fun testBackupDataSerializationWithFullRoutePointsAndAllTables() {
        val user = UserEntity(
            gender = "Male",
            age = 29,
            weightKg = 74f,
            heightCm = 182f
        )

        val encryptedGpsRoute = "enc:v1:dGVzdF9lbmNyeXB0ZWRfZ3BzX3JvdXRlX3BvaW50cw=="
        val sessions = listOf(
            WorkoutSessionEntity(
                id = 101L,
                startTime = 1700000000000L,
                endTime = 1700003600000L,
                durationSeconds = 3600L,
                totalDistanceMeters = 25000.0,
                elevationGainMeters = 350.0,
                avgSpeedKmh = 25.0,
                caloriesBurned = 650.0,
                wattsPerKg = 2.4,
                routePointsJson = encryptedGpsRoute,
                activityType = "CYCLING",
                isChallengeCompletion = true,
                rpe = 7,
                wellnessFeel = 4,
                avgCadenceSpm = 88.0,
                gaitStabilityScore = 92.5,
                weatherTempC = 24.5,
                weatherWindKmh = 12.0,
                weatherHumidityPct = 55.0,
                environmentStressFactor = 1.08,
                perceivedNotes = "Strong legs in tailwind finish"
            )
        )

        val routines = listOf(
            RoutineEntity(
                activityType = "CYCLING",
                interval = RoutineInterval.WEEKLY,
                metric = RoutineMetric.DISTANCE,
                targetValue = 100000.0,
                autoImprove = true,
                autoImprovePercentage = 0.05,
                currentPeriodStart = 1700000000000L,
                currentPeriodEnd = 1700604800000L,
                lastCompletedPeriodEnd = 0L
            )
        )

        val challenges = listOf(
            ChallengeEntity(
                id = 1L,
                activityType = "CYCLING",
                metric = ChallengeMetric.DISTANCE,
                targetValue = 20000.0,
                currentProgress = 20000.0,
                period = ChallengePeriod.DAY,
                status = ChallengeStatus.COMPLETED,
                createdAt = 1700000000000L,
                completedAt = 1700003600000L
            )
        )

        val personalRecords = listOf(
            PersonalRecordEntity(
                recordType = PersonalRecordType.FASTEST_10KM,
                activityType = "CYCLING",
                value = 1320.0,
                sessionId = 101L,
                achievedAt = 1700003600000L
            )
        )

        val trainingPlan = TrainingPlanEntity(
            id = 1,
            generatedAtMs = 1700000000000L,
            planJson = "{\"goal\":\"Build Endurance\",\"days\":[]}"
        )

        val wellnessCheckIns = listOf(
            WellnessCheckInEntity(
                id = 1L,
                timestampMs = 1700000000000L,
                type = CheckInType.PRE_WORKOUT,
                sleepQuality = 4,
                energyLevel = 4,
                muscleSoreness = 2,
                stressLevel = 2,
                hydrationScore = 4,
                notes = "Ready"
            )
        )

        val twinSnapshots = listOf(
            TwinSnapshotEntity(
                dateKey = "2023-11-14",
                updatedAtMs = 1700003600000L,
                readinessScore = 82,
                bodyBattery = 79,
                acuteLoad = 64.0,
                chronicLoad = 58.0,
                acwr = 1.10,
                strainScore = 54.0,
                recommendedIntensity = "TEMPO",
                weatherTempC = 24.5,
                windSpeedKmh = 12.0,
                humidityPct = 55.0,
                uvIndex = 4.2
            )
        )

        val backup = BackupData(
            user = user,
            sessions = sessions,
            routines = routines,
            challenges = challenges,
            personalRecords = personalRecords,
            trainingPlan = trainingPlan,
            wellnessCheckIns = wellnessCheckIns,
            twinSnapshots = twinSnapshots
        )

        val json = gson.toJson(backup)
        assertNotNull(json)
        assertTrue(json.contains(encryptedGpsRoute))
        assertTrue(json.contains("FASTEST_10KM"))
        assertTrue(json.contains("Build Endurance"))
        assertTrue(json.contains("PRE_WORKOUT"))

        val deserialized = gson.fromJson(json, BackupData::class.java)
        assertEquals(1, deserialized.sessions?.size)
        assertEquals(encryptedGpsRoute, deserialized.sessions?.first()?.routePointsJson)
        assertEquals(7, deserialized.sessions?.first()?.rpe)
        assertEquals(92.5, deserialized.sessions?.first()?.gaitStabilityScore ?: 0.0, 0.01)
        assertEquals(1, deserialized.personalRecords?.size)
        assertEquals(PersonalRecordType.FASTEST_10KM, deserialized.personalRecords?.first()?.recordType)
        assertNotNull(deserialized.trainingPlan)
        assertEquals(1, deserialized.trainingPlan?.id)
        assertEquals(1, deserialized.wellnessCheckIns?.size)
        assertEquals(1, deserialized.twinSnapshots?.size)
        assertEquals(82, deserialized.twinSnapshots?.first()?.readinessScore)
    }

    @Test
    fun testLegacyBackupCompatibilityWithoutNewFields() {
        val legacyJson = """
            {
              "user": {
                "gender": "Female",
                "age": 32,
                "weightKg": 60.0,
                "heightCm": 168.0
              },
              "sessions": [],
              "routines": [],
              "challenges": []
            }
        """.trimIndent()

        val deserialized = gson.fromJson(legacyJson, BackupData::class.java)
        assertNotNull(deserialized.user)
        assertEquals(32, deserialized.user?.age)
        assertTrue(deserialized.personalRecords.isNullOrEmpty())
        assertNull(deserialized.trainingPlan)
    }
}
