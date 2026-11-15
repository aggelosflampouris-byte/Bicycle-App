package com.fitnessapp.tracker.data.remote

import com.fitnessapp.tracker.data.local.CoachLanguage
import com.fitnessapp.tracker.data.local.CoachPersona
import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.api.AiCoachApiService
import com.fitnessapp.tracker.data.remote.model.AiChatRequest
import com.fitnessapp.tracker.data.remote.model.AiChatResponse
import com.fitnessapp.tracker.engine.AcwrZone
import com.fitnessapp.tracker.engine.DigitalTwinState
import com.fitnessapp.tracker.engine.RecommendedIntensity
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AiCoachPromptTest {

    private val stubApi = object : AiCoachApiService {
        override suspend fun chatCompletion(authorization: String, request: AiChatRequest): Response<AiChatResponse> {
            throw UnsupportedOperationException("Network should not be called in prompt unit test")
        }
    }

    private val repository = AiCoachRepository(stubApi)
    private val athlete = UserEntity(age = 29, weightKg = 72f, heightCm = 176f, gender = "male")

    @Test
    fun `buildSystemPrompt injects Digital Twin state, microclimate, wellness signals and session telemetry`() {
        val session = WorkoutSessionEntity(
            totalDistanceMeters = 12_500.0,
            avgSpeedKmh = 11.4,
            caloriesBurned = 780.0,
            wattsPerKg = 3.1,
            activityType = "JOGGING",
            rpe = 8,
            avgCadenceSpm = 172.0,
            gaitStabilityScore = 84.0
        )
        val twin = DigitalTwinState(
            bodyBattery = 64,
            readinessScore = 58,
            acwr = 1.38,
            acwrZone = AcwrZone.ELEVATED_RISK,
            environmentStressFactor = 1.19,
            hydrationRateLitersPerHour = 0.78,
            recommendedIntensity = RecommendedIntensity.AEROBIC_ZONE_2,
            todayStrain = 92.0,
            environment = EnvironmentSnapshot(
                temperatureC = 28.0,
                apparentTemperatureC = 30.0,
                windSpeedKmh = 18.0,
                uvIndex = 7.4,
                conditionLabel = "Clear"
            ),
            latestCheckIn = WellnessCheckInEntity(
                type = CheckInType.PRE_WORKOUT,
                sleepQuality = 3,
                energyLevel = 4,
                muscleSoreness = 4,
                stressLevel = 2,
                hydrationScore = 3
            ),
            riskFlags = listOf("High outdoor wind", "Thermal load")
        )

        val prompt = repository.buildSystemPrompt(
            user = athlete,
            session = session,
            activityType = "JOGGING",
            persona = CoachPersona.DATA_SCIENTIST,
            language = CoachLanguage.GREEK,
            twinState = twin
        )

        assertTrue(prompt.contains("[DIGITAL TWIN STATE]"))
        assertTrue(prompt.contains("Body Battery: 64%"))
        assertTrue(prompt.contains("Readiness: 58/100"))
        assertTrue(prompt.contains("ACWR: 1.38"))
        assertTrue(prompt.contains("[OUTDOOR MICROCLIMATE]"))
        assertTrue(prompt.contains("Env Stress Factor: x1.19"))
        assertTrue(prompt.contains("[WELLNESS SIGNALS]"))
        assertTrue(prompt.contains("Soreness 4/5"))
        assertTrue(prompt.contains("RPE: 8/10"))
        assertTrue(prompt.contains("Cadence: 172 spm"))
        assertTrue(prompt.contains("Form Stability: 84%"))
        assertTrue(prompt.contains("Greek (Ελληνικά)"))
        assertTrue(prompt.contains("Sports Scientist"))
    }

    @Test
    fun `buildSystemPrompt stays clean when twinState is omitted`() {
        val prompt = repository.buildSystemPrompt(
            user = athlete,
            session = null,
            activityType = "CYCLING",
            persona = CoachPersona.SUPPORTIVE,
            language = CoachLanguage.ENGLISH,
            twinState = null
        )

        assertTrue(prompt.contains("[USER BIOMETRICS]"))
        assertTrue(prompt.contains("[LATEST SESSION]"))
        assertFalse(prompt.contains("[DIGITAL TWIN STATE]"))
        assertTrue(prompt.contains("GUARDRAILS (STRICT)"))
    }
}

