package com.example.smartcyclingtracker.data.remote

import com.example.smartcyclingtracker.BuildConfig
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.remote.api.HfApiService
import com.example.smartcyclingtracker.data.remote.model.HfChatRequest
import com.example.smartcyclingtracker.data.remote.model.HfMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import com.example.smartcyclingtracker.data.local.entity.DailyPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for Hugging Face AI chat interactions.
 * Uses the OpenAI-compatible /v1/chat/completions endpoint via router.huggingface.co.
 * Model: Qwen/Qwen2.5-72B-Instruct (free serverless tier).
 *
 * Reads HF token from:
 *  1. User-supplied key in DataStore (Settings screen override)
 *  2. BuildConfig.GEMINI_API_KEY (local.properties key name kept for backwards compat)
 */
@Singleton
class GeminiRepository @Inject constructor(
    private val apiService: HfApiService
) {

    /**
     * Resolve the HF token from BuildConfig (baked-in via local.properties / CI secret).
     */
    private fun resolveApiKey(): String? {
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey.startsWith("hf_")) return buildKey
        return null
    }

    /**
     * Builds the AI Coach RAG system prompt with user & session data.
     */
    fun buildSystemPrompt(user: UserEntity, session: WorkoutSessionEntity?, activityType: String = "CYCLING"): String {
        val distance = session?.let { "%.1f".format(it.totalDistanceMeters / 1000.0) } ?: "N/A"
        val speed = session?.let { "%.1f".format(it.avgSpeedKmh) } ?: "N/A"
        val wattsPerKg = session?.let { "%.2f".format(it.wattsPerKg) } ?: "N/A"
        val calories = session?.let { "%.0f".format(it.caloriesBurned) } ?: "N/A"

        val activityName = when (activityType) {
            "WALKING" -> "walking"
            "JOGGING" -> "jogging"
            else -> "cycling"
        }
        val coachRole = when (activityType) {
            "WALKING" -> "professional walking/hiking coach"
            "JOGGING" -> "professional jogging coach"
            else -> "professional cycling coach"
        }

        return """
            Act as a $coachRole, acting as my "AI Coach".
            [USER DATA] Gender: ${user.gender}, Age: ${user.age}, Height: ${"%.0f".format(user.heightCm)}cm, Weight: ${"%.0f".format(user.weightKg)}kg.
            [RECENT SESSION] Distance: ${distance}km, Avg Speed: ${speed}km/h, Performance: ${wattsPerKg} W/kg, Calories: ${calories}.
            Analyze this strictly for $activityName progress. Keep it short, encouraging, and do not give medical advice.
            
            GUARDRAILS (STRICT):
            - You MUST refuse to answer ANY questions not related to $activityName, fitness, or performance.
            - If asked "how is your day?", "am I pretty?", or general off-topic questions, politely decline and steer the conversation back to $activityName.
            - DO NOT hallucinate features, ride data, or facts. Stick strictly to the data provided.
            - DO NOT recommend, suggest, or mention other tracking or fitness apps (e.g., Strava, Garmin Connect, Komoot). You must ONLY recommend features of this app.
            - DO NOT use any Markdown formatting whatsoever. No asterisks, no hashes, no lists. Output plain, readable text only.
        """.trimIndent()
    }

    /**
     * Send a chat message to Hugging Face Inference API and emit the response text.
     * Uses Qwen2.5-72B (an open-source model on the free HF tier).
     * Emits a user-friendly error string if the API token is missing or the request fails.
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
                "The API token is missing from the app's internal configuration (BuildConfig). " +
                "Please rebuild the app with a valid GEMINI_API_KEY."
            )
            return@flow
        }

        // Build the messages list: system prompt + chat history + new user message
        val messages = buildList {
            add(HfMessage(role = "system", content = systemPrompt))
            history.forEach { (role, text) ->
                // Map "model" role (Gemini convention) to "assistant" (OpenAI convention)
                val normalizedRole = if (role == "model") "assistant" else role
                add(HfMessage(role = normalizedRole, content = text))
            }
            add(HfMessage(role = "user", content = userMessage))
        }

        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = messages,
            maxTokens = 512,
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
                    400 -> emit("⚠️ **Invalid request (400)** — check your HF token format.\n\nDetails: $errBody")
                    401, 403 -> emit(
                        "⚠️ **Token rejected ($code)** — your Hugging Face token may be invalid, expired, or missing the 'Inference' permission.\n\n" +
                        "Create a new token at [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens) with **'Make calls to Inference Providers'** enabled, then update it in Settings."
                    )
                    429 -> emit("⚠️ **Rate limit reached (429)** — you've hit the HF free tier limit. Wait a moment and try again.")
                    503 -> emit("⚠️ **Model loading (503)** — the model is warming up. Wait ~30 seconds and try again.")
                    else -> emit("⚠️ **Error $code** — $errBody")
                }
                return@flow
            }

            val body = response.body()
            val hfError = body?.error
            if (hfError != null) {
                emit("⚠️ **API Error** — ${hfError.message ?: "Unknown error from HF API"}")
                return@flow
            }

            val text = body?.choices?.firstOrNull()?.message?.content
            if (!text.isNullOrBlank()) {
                emit(text.trim())
            } else {
                emit("I'm having trouble responding right now. Please try again.")
            }

        } catch (e: Exception) {
            emit("⚠️ **Connection error** — ${e.message}\n\nCheck your internet connection and try again.")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a 7-day training plan formatted as JSON.
     */
    suspend fun generateWeeklyPlan(
        user: UserEntity,
        recentSessions: List<WorkoutSessionEntity>
    ): List<DailyPlan>? {
        val apiKey = resolveApiKey() ?: return null
        
        val summary = if (recentSessions.isEmpty()) "No recent sessions." else {
            val totalDist = recentSessions.sumOf { it.totalDistanceMeters } / 1000.0
            val avgSpeed = recentSessions.map { it.avgSpeedKmh }.average()
            "Recent week total distance: ${"%.1f".format(totalDist)}km, average speed: ${"%.1f".format(avgSpeed)}km/h."
        }
        
        val systemPrompt = """
            You are an elite cycling and running coach. Create a structured 7-day training plan for a user (${user.age} yrs, ${"%.0f".format(user.weightKg)} kg).
            $summary
            
            You MUST output ONLY a raw JSON array of 7 items (one for each day, starting Monday). Do NOT include markdown blocks, text, or formatting outside the JSON array.
            Format exactly like this:
            [
              {"day": "Monday", "title": "Rest Day", "description": "Active recovery...", "targetDistance": 0.0},
              ...
            ]
        """.trimIndent()
        
        val request = HfChatRequest(
            model = HfApiService.MODEL,
            messages = listOf(HfMessage("system", systemPrompt)),
            maxTokens = 800,
            temperature = 0.5f
        )
        
        return try {
            val response = apiService.chatCompletion("Bearer $apiKey", request)
            if (response.isSuccessful) {
                var jsonStr = response.body()?.choices?.firstOrNull()?.message?.content ?: return null
                // Clean up possible markdown wrappers
                jsonStr = jsonStr.replace("```json", "").replace("```", "").trim()
                val listType = object : TypeToken<List<DailyPlan>>() {}.type
                Gson().fromJson<List<DailyPlan>>(jsonStr, listType)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
