package com.example.smartcyclingtracker.data.remote.api

import com.example.smartcyclingtracker.data.remote.model.GeminiRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit interface for Google Gemini 1.5 Flash API.
 * Uses @Streaming to get raw SSE stream from the API.
 * The API key must be passed as a query param.
 */
interface GeminiApiService {

    /**
     * Streaming chat completion endpoint.
     * Returns raw ResponseBody so we can read SSE chunks as they arrive.
     */
    @Streaming
    @POST("v1beta/models/gemini-1.5-flash:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Query("key") apiKey: String,
        @Query("alt") alt: String = "sse",
        @Body request: GeminiRequest
    ): Response<ResponseBody>

    /**
     * Non-streaming endpoint for single response (fallback).
     */
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<com.example.smartcyclingtracker.data.remote.model.GeminiResponse>

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}
