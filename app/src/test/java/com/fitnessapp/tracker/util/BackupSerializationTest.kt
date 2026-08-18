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
                isChallengeCompletion = true
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

        val backup = BackupData(
            user = user,
            sessions = sessions,
            routines = routines,
            challenges = challenges,
            personalRecords = personalRecords,
            trainingPlan = trainingPlan
        )

        val json = gson.toJson(backup)
        assertNotNull(json)
        assertTrue(json.contains(encryptedGpsRoute))
        assertTrue(json.contains("FASTEST_10KM"))
        assertTrue(json.contains("Build Endurance"))

        val deserialized = gson.fromJson(json, BackupData::class.java)
        assertEquals(1, deserialized.sessions?.size)
        assertEquals(encryptedGpsRoute, deserialized.sessions?.first()?.routePointsJson)
        assertEquals(1, deserialized.personalRecords?.size)
        assertEquals(PersonalRecordType.FASTEST_10KM, deserialized.personalRecords?.first()?.recordType)
        assertNotNull(deserialized.trainingPlan)
        assertEquals(1, deserialized.trainingPlan?.id)
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
