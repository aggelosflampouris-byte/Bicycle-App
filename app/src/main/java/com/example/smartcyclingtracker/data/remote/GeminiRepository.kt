package com.example.smartcyclingtracker.data.remote

import com.example.smartcyclingtracker.BuildConfig
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Gemini AI interactions.
 * Implements RAG by injecting Room DB data into the system prompt.
 * Parses SSE stream chunks and emits text deltas as a Flow.
 */
@Singleton
class GeminiRepository @Inject constructor(
    private val apiService: GeminiApiService,
    private val gson: Gson
) {
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

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
     */
    fun streamChat(
        userMessage: String,
        systemPrompt: String,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val messages = buildList {
            // Add conversation history
            history.forEach { (role, text) ->
                add(Content(role = role, parts = listOf(Part(text = text))))
            }
            // Add current user message
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
                emit("[Error ${response.code()}]: ${response.errorBody()?.string() ?: "Unknown error"}")
                return@flow
            }

            val body = response.body() ?: run {
                emit("[Error]: Empty response body")
                return@flow
            }

            // Parse SSE stream — each chunk is a JSON object prefixed with "data: "
            body.source().use { source ->
                val buffer = okio.Buffer()
                while (!source.exhausted()) {
                    source.read(buffer, 8192)
                    val chunk = buffer.readUtf8()
                    // SSE lines look like: "data: {...json...}"
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
            emit("[Connection error]: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
