package com.fitnessapp.tracker.data.remote.model

import com.google.gson.annotations.SerializedName

// ===== HF / OpenAI-COMPATIBLE REQUEST MODELS =====

data class HfChatRequest(
    val model: String,
    val messages: List<HfMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    @SerializedName("top_p")
    val topP: Float = 0.9f
)

data class HfMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

// ===== HF / OpenAI-COMPATIBLE RESPONSE MODELS =====

data class HfChatResponse(
    val id: String?,
    val choices: List<HfChoice>?,
    val error: HfError?
)

data class HfChoice(
    val message: HfMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?,
    val index: Int?
)

data class HfError(
    val message: String?,
    val type: String?,
    val code: String?
)
