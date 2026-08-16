package com.fitnessapp.tracker.data.remote

import com.fitnessapp.tracker.BuildConfig
import com.fitnessapp.tracker.data.local.CoachPersona
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.api.HfApiService
import com.fitnessapp.tracker.data.remote.model.HfChatRequest
import com.fitnessapp.tracker.data.remote.model.HfMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import com.fitnessapp.tracker.data.local.entity.DailyPlan
import com.fitnessapp.tracker.ui.summary.LapSummary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * Repository for Hugging Face AI interactions powered by Qwen 2.5 (72B Instruct).
 * Uses the OpenAI-compatible /v1/chat/completions endpoint.
 */
@Singleton
class GeminiRepository @Inject constructor(
    private val apiService: HfApiService
) {

    /**
     * Resolve the HF token from BuildConfig.
     */
    private fun resolveApiKey(): String? {
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey.startsWith("hf_")) return buildKey
        return null
    }

    /**
     * Builds the AI Coach RAG system prompt with user, session data, persona, and locale.
     */
    fun buildSystemPrompt(
        user: UserEntity,
        session: WorkoutSessionEntity?,
        activityType: String = "CYCLING",
        persona: CoachPersona = CoachPersona.SUPPORTIVE
    ): String {
        val distance = session?.let { "%.1f".format(it.totalDistanceMeters / 1000.0) } ?: "N/A"
        val speed = session?.let { "%.1f".format(it.avgSpeedKmh) } ?: "N/A"
        val wattsPerKg = session?.let { "%.2f".format(it.wattsPerKg) } ?: "N/A"
        val calories = session?.let { "%.0f".format(it.caloriesBurned) } ?: "N/A"

        val activityName = when (activityType) {
            "WALKING" -> "walking"
            "JOGGING" -> "jogging"
            else -> "cycling"
        }

        val personaInstructions = when (persona) {
            CoachPersona.SUPPORTIVE -> """
                PERSONA: Supportive Mentor
                - Tone: Empathetic, motivating, positive, and deeply encouraging.
                - Style: Celebrate consistency, promote mental wellbeing, and gently guide technique improvements.
            """.trimIndent()
            CoachPersona.DRILL_SERGEANT -> """
                PERSONA: Pro Drill Sergeant
                - Tone: Intense, demanding, direct, high-energy, and challenge-driven.
                - Style: Zero excuses. Push the athlete out of their comfort zone, demand mental fortitude and gritty effort.
            """.trimIndent()
            CoachPersona.DATA_SCIENTIST -> """
                PERSONA: Sports Scientist & Metric Geek
                - Tone: Analytical, objective, precise, and scientifically grounded.
                - Style: Emphasize wattage power curves, aerobic efficiency, pacing variance, recovery ratios, and metabolic optimization.
            """.trimIndent()
        }

        val languageName = Locale.getDefault().displayLanguage

        return """
            Act as an elite personal coach for $activityName.
            $personaInstructions

            [USER BIOMETRICS] Gender: ${user.gender}, Age: ${user.age}, Height: ${"%.0f".format(user.heightCm)}cm, Weight: ${"%.0f".format(user.weightKg)}kg.
            [LATEST SESSION] Distance: ${distance}km, Avg Speed: ${speed}km/h, Output: ${wattsPerKg} W/kg, Energy: ${calories} kcal.
            
            [LANGUAGE & LOCALIZATION]
            - Respond naturally in $languageName (or match the user's input language).
            - Use proper localized athletic terms.

            GUARDRAILS (STRICT):
            - You MUST refuse to answer ANY questions not related to $activityName, athletic fitness, nutrition, or athletic performance.
            - If asked off-topic questions, politely decline and steer the conversation back to fitness.
            - DO NOT hallucinate features, ride data, or facts. Stick strictly to the data provided.
            - DO NOT recommend or mention other third-party tracking apps (e.g., Strava, Garmin Connect, Komoot). Recommend features of this app only.
            - Keep responses concise, impactful, and conversational. Avoid walls of raw text.
        """.trimIndent()
    }

    /**
     * Send a chat message to Hugging Face Inference API and emit the response text.
     */
    fun streamChat(
        userMessage: String,
        systemPrompt: String,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val apiKey = resolveApiKey()
        if (apiKey == null) {
            emit(
                "⚠️ **No Hugging Face token configured.**\n\n" +
                "The API token is missing from BuildConfig. Please configure a valid token in local.properties or Settings."
            )
            return@flow
        }

        val messages = buildList {
            add(HfMessage(role = "system", content = systemPrompt))
            history.forEach { (role, text) ->
                val normalizedRole = if (role == "model") "assistant" else role
                add(HfMessage(role = normalizedRole, content = text))
            }
            add(HfMessage(role = "user", content = userMessage))
        }

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = messages,
            maxTokens = 600,
            temperature = 0.7f
        )

        try {
            val response = apiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val code = response.code()
                val errBody = response.errorBody()?.string() ?: "Unknown error"
                when (code) {
                    400 -> emit("⚠️ **Invalid request (400)** — $errBody")
                    401, 403 -> emit("⚠️ **Token rejected ($code)** — Verify Hugging Face Inference permissions.")
                    429 -> emit("⚠️ **Rate limit reached (429)** — Free tier limit hit. Please wait a moment.")
                    503 -> emit("⚠️ **Model warming up (503)** — Please retry in ~20 seconds.")
                    else -> emit("⚠️ **Error $code** — $errBody")
                }
                return@flow
            }

            val body = response.body()
            val text = body?.choices?.firstOrNull()?.message?.content
            if (!text.isNullOrBlank()) {
                emit(text.trim())
            } else {
                emit("I'm having trouble responding right now. Please try again.")
            }
        } catch (e: Exception) {
            emit("⚠️ **Connection error** — ${e.message}\n\nCheck your internet connection.")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Option 1: Multi-Week Fatigue & Fitness Progression Analysis
     */
    suspend fun analyzeFatigueAndFitness(
        user: UserEntity,
        sessions: List<WorkoutSessionEntity>,
        persona: CoachPersona = CoachPersona.SUPPORTIVE
    ): String? {
        val apiKey = resolveApiKey() ?: return null
        if (sessions.isEmpty()) return "No workout history recorded yet. Complete a few workouts to unlock fatigue & fitness trend analysis!"

        val totalWorkouts = sessions.size
        val totalDistanceKm = sessions.sumOf { it.totalDistanceMeters } / 1000.0
        val totalDurationHours = sessions.sumOf { it.durationSeconds } / 3600.0
        val avgSpeed = sessions.map { it.avgSpeedKmh }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val last7DaysSessions = sessions.filter { it.startTime >= sevenDaysAgo }
        val last7DaysKm = last7DaysSessions.sumOf { it.totalDistanceMeters } / 1000.0

        val prompt = """
            You are an elite sports scientist and personal coach. Analyze this athlete's multi-week training load and fatigue progression.
            
            [ATHLETE PROFILE] Age: ${user.age}, Weight: ${"%.0f".format(user.weightKg)}kg, Gender: ${user.gender}
            [CAREER STATS] Total Workouts: $totalWorkouts, Total Distance: ${"%.1f".format(totalDistanceKm)}km, Total Time: ${"%.1f".format(totalDurationHours)}h, Lifetime Avg Speed: ${"%.1f".format(avgSpeed)}km/h.
            [ACUTE 7-DAY LOAD] Last 7 Days Volume: ${"%.1f".format(last7DaysKm)}km across ${last7DaysSessions.size} workouts.
            
            Provide a crisp, professional breakdown in plain text formatted with these clear sections:
            1. 🔋 Fatigue Level & Recovery Score (Score out of 10)
            2. ⚠️ Overtraining Risk (Low / Moderate / High with explanation)
            3. 📈 Fitness & Endurance Trajectory (Progress analysis)
            4. 🎯 Milestone Readiness (What distance/milestone they are ready to tackle next)
            5. 💡 Immediate Training/Rest Recommendation for the next 48 hours
        """.trimIndent()

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = listOf(
                HfMessage("system", buildSystemPrompt(user, sessions.firstOrNull(), "CYCLING", persona)),
                HfMessage("user", prompt)
            ),
            maxTokens = 850,
            temperature = 0.6f
        )

        return try {
            val response = apiService.chatCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Option 2: Structured JSON-Mode Adaptive Training Plan Generator
     */
    suspend fun generateWeeklyPlan(
        user: UserEntity,
        recentSessions: List<WorkoutSessionEntity>,
        goal: String = "Balanced Endurance",
        persona: CoachPersona = CoachPersona.SUPPORTIVE
    ): List<DailyPlan>? {
        val apiKey = resolveApiKey() ?: return null

        val summary = if (recentSessions.isEmpty()) "No recent workouts logged." else {
            val totalDist = recentSessions.sumOf { it.totalDistanceMeters } / 1000.0
            val avgSpeed = recentSessions.map { it.avgSpeedKmh }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
            "Recent week volume: ${"%.1f".format(totalDist)}km, average pace/speed: ${"%.1f".format(avgSpeed)}km/h."
        }

        val systemPrompt = """
            You are a world-class training director. Create a structured 7-day adaptive training plan tailored for:
            [GOAL] $goal
            [ATHLETE] Age: ${user.age}, Weight: ${"%.0f".format(user.weightKg)}kg, Gender: ${user.gender}
            [RECENT DATA] $summary
            
            OUTPUT RULES (MANDATORY):
            - Output ONLY a raw JSON array containing exactly 7 daily items starting from Monday to Sunday.
            - No markdown blocks (no ```json wrappers), no commentary, no preamble.
            - Ensure targets (targetDistance in km) are realistic numbers (e.g. 0.0 for rest days).
            - Schema:
            [
              {"day": "Monday", "title": "...", "description": "...", "targetDistance": 0.0, "isCompleted": false},
              ...
            ]
        """.trimIndent()

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = listOf(HfMessage("system", systemPrompt)),
            maxTokens = 950,
            temperature = 0.4f
        )

        return try {
            val response = apiService.chatCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                var jsonStr = response.body()?.choices?.firstOrNull()?.message?.content ?: return null
                jsonStr = jsonStr.replace("```json", "").replace("```", "").trim()
                val listType = object : TypeToken<List<DailyPlan>>() {}.type
                Gson().fromJson<List<DailyPlan>>(jsonStr, listType)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Option 3: Tactical Post-Ride Debrief
     */
    suspend fun generateTacticalDebrief(
        user: UserEntity,
        session: WorkoutSessionEntity,
        lapSummaries: List<LapSummary>?,
        persona: CoachPersona = CoachPersona.SUPPORTIVE
    ): String? {
        val apiKey = resolveApiKey() ?: return null

        val lapsInfo = if (!lapSummaries.isNullOrEmpty()) {
            lapSummaries.joinToString("\n") { lap ->
                "Lap ${lap.lap}: ${"%.2f".format(lap.distanceMeters / 1000.0)}km in ${lap.durationSeconds}s (Avg ${"%.1f".format(lap.avgSpeedKmh)} km/h)"
            }
        } else {
            "Total Distance: ${"%.2f".format(session.totalDistanceMeters / 1000.0)}km, Duration: ${session.durationSeconds}s, Avg Speed: ${"%.1f".format(session.avgSpeedKmh)} km/h, Elevation Gain: ${"%.0f".format(session.elevationGainMeters)}m."
        }

        val prompt = """
            Perform a tactical coaching debrief for this completed workout session:
            
            [SESSION DATA]
            $lapsInfo
            [CALORIES & OUTPUT] Burned: ${"%.0f".format(session.caloriesBurned)} kcal, Specific Power: ${"%.2f".format(session.wattsPerKg)} W/kg.
            
            Analyze the workout tactics in concise bullet points:
            1. ⏱️ Pacing Consistency & Split Analysis (Did pace drop or stay steady?)
            2. ⛰️ Elevation & Power Management
            3. 💡 Tactical Improvements for Next Ride (Gear choice, cadence, pacing distribution)
        """.trimIndent()

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = listOf(
                HfMessage("system", buildSystemPrompt(user, session, session.activityType, persona)),
                HfMessage("user", prompt)
            ),
            maxTokens = 750,
            temperature = 0.5f
        )

        return try {
            val response = apiService.chatCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Option 4: Pre-Ride Strategy & Nutrition/Hydration Briefing
     */
    suspend fun generatePreRideBriefing(
        user: UserEntity,
        targetDistanceKm: Double,
        weatherCondition: String,
        tempC: Double,
        windSpeedKmh: Double,
        windDirection: String,
        persona: CoachPersona = CoachPersona.SUPPORTIVE
    ): String? {
        val apiKey = resolveApiKey() ?: return null

        val prompt = """
            Prepare a tactical pre-ride strategy and nutrition/hydration gameplan for this upcoming ride:
            
            [TARGET] Planned Distance: ${"%.1f".format(targetDistanceKm)} km
            [ATHLETE] Weight: ${"%.0f".format(user.weightKg)} kg, Age: ${user.age}
            [WEATHER] Condition: $weatherCondition, Temperature: ${"%.0f".format(tempC)}°C, Wind: ${"%.1f".format(windSpeedKmh)} km/h from $windDirection.
            
            Provide a clear, actionable guide with:
            1. 💨 Wind & Weather Pacing Strategy (How to handle temperature and wind)
            2. 💧 Hydration Target (Estimated ml of water/electrolytes per hour)
            3. 🍌 Carbohydrate Replenishment (Target grams of carbs per hour for this intensity)
            4. 🚴 Pre-Ride Dynamic Warmup (3 quick points)
        """.trimIndent()

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = listOf(
                HfMessage("system", buildSystemPrompt(user, null, "CYCLING", persona)),
                HfMessage("user", prompt)
            ),
            maxTokens = 800,
            temperature = 0.5f
        )

        return try {
            val response = apiService.chatCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
