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

/**
 * Repository for Hugging Face AI chat interactions.
 * Uses the OpenAI-compatible /v1/chat/completions endpoint via router.huggingface.co.
 * Model: Qwen/Qwen2.5-3B-Instruct (quantized, free serverless tier).
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
     * Builds the VeloCoach RAG system prompt with user & session data.
     */
    fun buildSystemPrompt(user: UserEntity, session: WorkoutSessionEntity?): String {
        val distance = session?.let { "%.1f".format(it.totalDistanceMeters / 1000.0) } ?: "N/A"
        val speed = session?.let { "%.1f".format(it.avgSpeedKmh) } ?: "N/A"
        val wattsPerKg = session?.let { "%.2f".format(it.wattsPerKg) } ?: "N/A"
        val calories = session?.let { "%.0f".format(it.caloriesBurned) } ?: "N/A"

        return """
            Act as "VeloCoach", a professional cycling coach.
            [USER DATA] Gender: ${user.gender}, Age: ${user.age}, Height: ${"%.0f".format(user.heightCm)}cm, Weight: ${"%.0f".format(user.weightKg)}kg.
            [RECENT SESSION] Distance: ${distance}km, Avg Speed: ${speed}km/h, Performance: ${wattsPerKg} W/kg, Calories: ${calories}.
            Analyze this strictly for cycling progress. Keep it short, encouraging, and do not give medical advice.
        """.trimIndent()
    }

    /**
     * Send a chat message to Hugging Face Inference API and emit the response text.
     * Uses Qwen2.5-3B-Instruct (a quantized open-source model on the free HF tier).
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
}
