package com.example.smartcyclingtracker.data.remote.api

import com.example.smartcyclingtracker.data.remote.model.HfChatRequest
import com.example.smartcyclingtracker.data.remote.model.HfChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for Hugging Face Inference API (OpenAI-compatible).
 * Uses the serverless inference endpoint which hosts open-source models.
 * The HF token is passed as a Bearer Authorization header.
 *
 * Model used: Qwen/Qwen2.5-72B-Instruct available for free on the HF Serverless Inference tier.
 */
interface HfApiService {

    /**
     * Chat completion endpoint (non-streaming).
     * Compatible with OpenAI Chat API format.
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: HfChatRequest
    ): Response<HfChatResponse>

    companion object {
        const val BASE_URL = "https://router.huggingface.co/"
        /** Default quantized model served via HF Serverless Inference (free tier). */
        const val MODEL = "Qwen/Qwen2.5-72B-Instruct"
    }
}
