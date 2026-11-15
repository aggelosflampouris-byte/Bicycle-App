package com.fitnessapp.tracker.data.remote.api

import com.fitnessapp.tracker.data.remote.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Keyless Open-Meteo REST API for outdoor weather and microclimate stress context.
 */
interface OpenMeteoApiService {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m,uv_index,weather_code",
        @Query("hourly") hourly: String =
            "temperature_2m,apparent_temperature,wind_speed_10m,uv_index,precipitation_probability",
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}

