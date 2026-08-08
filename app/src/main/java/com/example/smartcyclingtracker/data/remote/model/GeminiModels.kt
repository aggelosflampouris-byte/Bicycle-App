package com.example.smartcyclingtracker.data.remote.model

import com.google.gson.annotations.SerializedName

// ===== REQUEST MODELS =====

data class GeminiRequest(
    val contents: List<Content>,
    @SerializedName("generationConfig")
    val generationConfig: GenerationConfig = GenerationConfig(),
    @SerializedName("systemInstruction")
    val systemInstruction: SystemInstruction? = null
)

data class Content(
    val role: String,
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class SystemInstruction(
    val parts: List<Part>
)

data class GenerationConfig(
    val temperature: Float = 0.7f,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 1024,
    @SerializedName("topP")
    val topP: Float = 0.95f
)

// ===== RESPONSE MODELS =====

data class GeminiResponse(
    val candidates: List<Candidate>?,
    val error: GeminiError?
)

data class Candidate(
    val content: Content?,
    @SerializedName("finishReason")
    val finishReason: String?
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
