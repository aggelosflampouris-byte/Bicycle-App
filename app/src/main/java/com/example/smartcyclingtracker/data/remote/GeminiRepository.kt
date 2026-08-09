package com.example.smartcyclingtracker.data.remote

import com.example.smartcyclingtracker.BuildConfig
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.remote.api.GeminiApiService
import com.example.smartcyclingtracker.data.remote.model.Content
import com.example.smartcyclingtracker.data.remote.model.GeminiRequest
import com.example.smartcyclingtracker.data.remote.model.Part
import com.example.smartcyclingtracker.data.remote.model.SystemInstruction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Gemini AI interactions.
 * Implements RAG by injecting Room DB data into the system prompt.
 * Reads API key from DataStore (user-supplied) with BuildConfig fallback.
 */
@Singleton
class GeminiRepository @Inject constructor(
    private val apiService: GeminiApiService,
    private val settingsRepo: SettingsRepository,
    private val gson: Gson
) {

    /**
     * Resolve the API key with the following priority:
     * 1. User-supplied key in DataStore (Settings screen override)
     * 2. BuildConfig key — read from local.properties (gitignored) or
     *    GEMINI_API_KEY GitHub Actions secret at build time
     * Returns null if neither source has a valid key.
     */
    private suspend fun resolveApiKey(): String? {
        // User-supplied override takes top priority
        val stored = settingsRepo.geminiApiKey.first().trim()
        if (stored.isNotBlank()) return stored

        // Fall back to build-time key from local.properties / CI secret
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank()) return buildKey

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
     * Stream a chat response from Gemini 1.5 Flash.
     * Emits each text chunk as it arrives from the SSE stream.
     * Emits a user-friendly error if the API key is missing or invalid.
     */
    fun streamChat(
        userMessage: String,
        systemPrompt: String,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val apiKey = resolveApiKey()
        if (apiKey == null) {
            emit(
                "⚠️ **No Gemini API key configured.**\n\n" +
                "Go to the **Profile → Settings tab**, scroll to the API Key section, " +
                "and paste your key from [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey).\n\n" +
                "A free key allows ~60 requests/minute."
            )
            return@flow
        }

        val messages = buildList {
            history.forEach { (role, text) ->
                add(Content(role = role, parts = listOf(Part(text = text))))
            }
            add(Content(role = "user", parts = listOf(Part(text = userMessage))))
        }

        val request = GeminiRequest(
            contents = messages,
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = apiService.streamGenerateContent(
                apiKey = apiKey,
                request = request
            )

            if (!response.isSuccessful) {
                val code = response.code()
                val errBody = response.errorBody()?.string() ?: "Unknown error"
                when (code) {
                    400 -> emit("⚠️ **Invalid request** — the API key format may be wrong.\n\nCheck your key in Settings.\n\nDetails: $errBody")
                    401, 403 -> emit("⚠️ **API key rejected (${code})** — your key may be invalid or expired.\n\nGet a new free key at [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey) and update it in Settings.")
                    429 -> emit("⚠️ **Rate limit reached** — you've hit the Gemini free tier limit. Wait a minute and try again.")
                    else -> emit("⚠️ **Error ${code}** — $errBody")
                }
                return@flow
            }

            val body = response.body() ?: run {
                emit("⚠️ Empty response from Gemini. Please try again.")
                return@flow
            }

            // Parse SSE stream — each chunk is a JSON object prefixed with "data: "
            body.source().use { source ->
                val buffer = okio.Buffer()
                while (!source.exhausted()) {
                    source.read(buffer, 8192)
                    val chunk = buffer.readUtf8()
                    chunk.lines()
                        .filter { it.startsWith("data:") }
                        .forEach { line ->
                            val jsonStr = line.removePrefix("data:").trim()
                            if (jsonStr == "[DONE]" || jsonStr.isEmpty()) return@forEach
                            try {
                                val parsed = gson.fromJson(
                                    jsonStr,
                                    com.example.smartcyclingtracker.data.remote.model.GeminiResponse::class.java
                                )
                                val text = parsed.candidates
                                    ?.firstOrNull()
                                    ?.content
                                    ?.parts
                                    ?.firstOrNull()
                                    ?.text
                                if (!text.isNullOrEmpty()) {
                                    emit(text)
                                }
                            } catch (_: Exception) {
                                // Skip malformed chunks
                            }
                        }
                }
            }
        } catch (e: Exception) {
            emit("⚠️ **Connection error** — ${e.message}\n\nCheck your internet connection and try again.")
        }
    }.flowOn(Dispatchers.IO)
}
