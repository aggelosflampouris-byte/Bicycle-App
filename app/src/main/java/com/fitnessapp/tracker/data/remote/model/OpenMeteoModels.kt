package com.fitnessapp.tracker.data.remote.model

import com.google.gson.annotations.SerializedName

data class OpenMeteoResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null
)

data class OpenMeteoCurrent(
    val time: String? = null,
    @SerializedName("temperature_2m")
    val temperatureC: Double? = null,
    @SerializedName("apparent_temperature")
    val apparentTemperatureC: Double? = null,
    @SerializedName("relative_humidity_2m")
    val humidityPct: Double? = null,
    val precipitation: Double? = null,
    @SerializedName("wind_speed_10m")
    val windSpeedKmh: Double? = null,
    @SerializedName("wind_direction_10m")
    val windDirectionDeg: Double? = null,
    @SerializedName("uv_index")
    val uvIndex: Double? = null,
    @SerializedName("weather_code")
    val weatherCode: Int? = null
)

data class OpenMeteoHourly(
    val time: List<String> = emptyList(),
    @SerializedName("temperature_2m")
    val temperatureC: List<Double> = emptyList(),
    @SerializedName("apparent_temperature")
    val apparentTemperatureC: List<Double> = emptyList(),
    @SerializedName("wind_speed_10m")
    val windSpeedKmh: List<Double> = emptyList(),
    @SerializedName("uv_index")
    val uvIndex: List<Double> = emptyList(),
    @SerializedName("precipitation_probability")
    val precipitationProbabilityPct: List<Int> = emptyList()
)

