package com.fitnessapp.tracker.data.remote.model

import com.google.gson.annotations.SerializedName

// ===== AI COACH / OPENAI-COMPATIBLE REQUEST MODELS =====

data class AiChatRequest(
    val model: String,
    val messages: List<AiChatMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    @SerializedName("top_p")
    val topP: Float = 0.9f
)

data class AiChatMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

// ===== AI COACH / OPENAI-COMPATIBLE RESPONSE MODELS =====

data class AiChatResponse(
    val id: String?,
    val choices: List<AiChatChoice>?,
    val error: AiChatError?
)

data class AiChatChoice(
    val message: AiChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?,
    val index: Int?
)

data class AiChatError(
    val message: String?,
    val type: String?,
    val code: String?
)
