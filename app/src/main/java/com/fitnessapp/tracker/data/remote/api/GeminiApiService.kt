package com.fitnessapp.tracker.data.remote.api

import com.fitnessapp.tracker.data.remote.model.HfChatRequest
import com.fitnessapp.tracker.data.remote.model.HfChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for Hugging Face Inference API (OpenAI-compatible).
 * Uses the serverless inference endpoint which hosts open-source models.
 * The HF token is passed as a Bearer Authorization header.
 *
 * Model used: Qwen/Qwen2.5-27B-Instruct — 27B parameter variant for significantly
 * faster inference latency vs the 72B variant, with comparable instruction-following quality.
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
        /** 27B parameter model for faster inference. Switched from 72B on 2026-10-06. */
        const val MODEL = "Qwen/Qwen2.5-Coder-32B-Instruct"
    }
}
