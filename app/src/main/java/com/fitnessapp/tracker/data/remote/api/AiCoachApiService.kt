package com.fitnessapp.tracker.data.remote.api

import com.fitnessapp.tracker.data.remote.model.AiChatRequest
import com.fitnessapp.tracker.data.remote.model.AiChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the OpenAI-compatible AI Coach inference endpoint.
 */
interface AiCoachApiService {

    /**
     * Chat completion endpoint (non-streaming).
     * Compatible with OpenAI Chat API format.
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: AiChatRequest
    ): Response<AiChatResponse>

    companion object {
        const val BASE_URL = "https://router.huggingface.co/"
        const val MODEL = "Qwen/Qwen2.5-32B-Instruct"
    }
}
