package com.fitnessapp.tracker.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.remote.api.OpenMeteoApiService
import com.fitnessapp.tracker.data.remote.model.OpenMeteoHourly
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.fitnessapp.tracker.util.DateFormats
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class EnvironmentSnapshot(
    val fetchedAtMs: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val temperatureC: Double = 20.0,
    val apparentTemperatureC: Double = 20.0,
    val humidityPct: Double = 55.0,
    val windSpeedKmh: Double = 8.0,
    val windDirectionDeg: Double = 0.0,
    val uvIndex: Double = 2.0,
    val precipitationProbPct: Int = 0,
    val weatherCode: Int = 0,
    val conditionLabel: String = "Clear",
    val bestWindowLabel: String? = null,
    val isOfflineFallback: Boolean = false
) {
    fun shortSummary(): String {
        val offlineTag = if (isOfflineFallback) " (cached)" else ""
        return "${"%.0f".format(temperatureC)}°C • ${"%.0f".format(windSpeedKmh)} km/h wind • $conditionLabel$offlineTag"
    }
}

@Singleton
class EnvironmentContextRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val openMeteoApi: OpenMeteoApiService,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "EnvironmentContextRepo"
        private const val CACHE_TTL_MS = 20 * 60 * 1000L
        private const val COORD_REFRESH_DELTA_DEG = 0.03

        internal fun isCacheFresh(
            cached: EnvironmentSnapshot,
            lat: Double?,
            lng: Double?,
            now: Long
        ): Boolean {
            if (now - cached.fetchedAtMs > CACHE_TTL_MS) return false
            if (lat != null && lng != null && cached.latitude != 0.0) {
                val moved = abs(lat - cached.latitude) > COORD_REFRESH_DELTA_DEG ||
                    abs(lng - cached.longitude) > COORD_REFRESH_DELTA_DEG
                if (moved) return false
            }
            return true
        }

        internal fun resolveCurrentPrecipProb(
            hourly: OpenMeteoHourly?,
            currentTimeIso: String?,
            fallbackHourIso: String = DateFormats.formatWeatherHourKey()
        ): Int {
            if (hourly == null || hourly.time.isEmpty() || hourly.precipitationProbabilityPct.isEmpty()) return 0
            val hourPrefix = currentTimeIso?.take(13) ?: fallbackHourIso
            val idx = hourly.time.indexOfFirst { it.startsWith(hourPrefix) }.takeIf { it >= 0 } ?: 0
            return hourly.precipitationProbabilityPct.getOrElse(idx) { 0 }
        }

        internal fun computeBestWorkoutWindow(
            hourly: OpenMeteoHourly?,
            currentHourIso: String = DateFormats.formatWeatherHourKey()
        ): String? {
            if (hourly == null || hourly.time.isEmpty()) return null
            val startIdx = hourly.time.indexOfFirst { it.take(13) >= currentHourIso }.coerceAtLeast(0)
            val endIdx = (startIdx + 12).coerceAtMost(hourly.time.lastIndex)
            if (startIdx > endIdx) return null

            var bestIdx = -1
            var bestPenalty = Double.MAX_VALUE
            for (i in startIdx..endIdx) {
                val hourOfDay = hourly.time[i].substringAfter("T", "").take(2).toIntOrNull() ?: continue
                if (hourOfDay !in 6..21) continue

                val appTemp = hourly.apparentTemperatureC.getOrElse(i) { 20.0 }
                val wind = hourly.windSpeedKmh.getOrElse(i) { 8.0 }
                val uv = hourly.uvIndex.getOrElse(i) { 2.0 }
                val rainProb = hourly.precipitationProbabilityPct.getOrElse(i) { 0 }

                val tempPenalty = abs(appTemp - 17.5) * 1.6
                val windPenalty = wind * 0.9
                val uvPenalty = (uv - 5.0).coerceAtLeast(0.0) * 3.0
                val rainPenalty = rainProb * 0.8
                val totalPenalty = tempPenalty + windPenalty + uvPenalty + rainPenalty

                if (totalPenalty < bestPenalty) {
                    bestPenalty = totalPenalty
                    bestIdx = i
                }
            }

            if (bestIdx == -1) return null
            val hourText = hourly.time[bestIdx].substringAfter("T", "").take(5)
            val tempText = "%.0f°C".format(hourly.temperatureC.getOrElse(bestIdx) { 20.0 })
            return if (hourText.isNotBlank()) "Best outdoor window today: $hourText ($tempText)" else null
        }

        internal fun decodeWmoWeatherCode(code: Int): String = when (code) {
            0 -> "Clear"
            1, 2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Outdoor"
        }
    }

    @Volatile
    private var memoryCache: EnvironmentSnapshot? = null

    suspend fun getEnvironmentContext(
        latitude: Double? = null,
        longitude: Double? = null,
        forceRefresh: Boolean = false
    ): EnvironmentSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = memoryCache ?: loadCachedSnapshot()

        val coords = resolveCoordinates(latitude, longitude, cached)
        if (!forceRefresh && cached != null && isCacheFresh(cached, coords?.first, coords?.second, now)) {
            return@withContext cached
        }

        if (coords == null) {
            return@withContext cached?.copy(isOfflineFallback = true)
                ?: EnvironmentSnapshot(isOfflineFallback = true)
        }

        try {
            val response = openMeteoApi.getForecast(
                latitude = coords.first,
                longitude = coords.second
            )
            val current = response.current
            if (current != null) {
                val precipProb = resolveCurrentPrecipProb(response.hourly, current.time)
                val snapshot = EnvironmentSnapshot(
                    fetchedAtMs = now,
                    latitude = coords.first,
                    longitude = coords.second,
                    temperatureC = current.temperatureC ?: 20.0,
                    apparentTemperatureC = current.apparentTemperatureC ?: (current.temperatureC ?: 20.0),
                    humidityPct = current.humidityPct ?: 55.0,
                    windSpeedKmh = current.windSpeedKmh ?: 8.0,
                    windDirectionDeg = current.windDirectionDeg ?: 0.0,
                    uvIndex = current.uvIndex ?: 2.0,
                    precipitationProbPct = precipProb,
                    weatherCode = current.weatherCode ?: 0,
                    conditionLabel = decodeWmoWeatherCode(current.weatherCode ?: 0),
                    bestWindowLabel = computeBestWorkoutWindow(response.hourly),
                    isOfflineFallback = false
                )
                memoryCache = snapshot
                settingsRepository.saveCachedEnvironmentJson(gson.toJson(snapshot))
                return@withContext snapshot
            }
        } catch (e: Exception) {
            Log.w(TAG, "Open-Meteo fetch failed, falling back to cached environment: ${e.message}")
        }

        cached?.copy(isOfflineFallback = true) ?: EnvironmentSnapshot(isOfflineFallback = true)
    }

    private suspend fun resolveCoordinates(
        lat: Double?,
        lng: Double?,
        cached: EnvironmentSnapshot?
    ): Pair<Double, Double>? {
        if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
            return lat to lng
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                val loc = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                if (loc != null) {
                    return loc.latitude to loc.longitude
                }
            } catch (_: Exception) {
                // Fall back to last cached coordinates when FusedLocation is unavailable.
            }
        }

        if (cached != null && (cached.latitude != 0.0 || cached.longitude != 0.0)) {
            return cached.latitude to cached.longitude
        }
        return null
    }

    private suspend fun loadCachedSnapshot(): EnvironmentSnapshot? {
        return try {
            val json = settingsRepository.cachedEnvironmentJson.first()
            if (json.isNullOrBlank()) null else gson.fromJson(json, EnvironmentSnapshot::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

