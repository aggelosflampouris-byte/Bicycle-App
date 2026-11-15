package com.fitnessapp.tracker.data.remote

import com.fitnessapp.tracker.data.remote.model.OpenMeteoHourly
import org.junit.Assert.*
import org.junit.Test

class EnvironmentContextLogicTest {

    @Test
    fun `computeBestWorkoutWindow avoids heat, storms, and sleeping hours`() {
        val hourly = OpenMeteoHourly(
            time = listOf(
                "2026-11-15T04:00", // asleep hour -> skipped
                "2026-11-15T08:00", // crisp 17C, calm wind, 0% rain -> ideal
                "2026-11-15T13:00", // hot 33C, high UV 8, gusty
                "2026-11-15T16:00"  // 80% rainstorm
            ),
            temperatureC = listOf(14.0, 17.0, 31.0, 19.0),
            apparentTemperatureC = listOf(14.0, 17.2, 33.5, 19.0),
            windSpeedKmh = listOf(4.0, 6.0, 22.0, 26.0),
            uvIndex = listOf(0.0, 3.0, 8.5, 2.0),
            precipitationProbabilityPct = listOf(0, 5, 15, 85)
        )

        val window = EnvironmentContextRepository.computeBestWorkoutWindow(
            hourly = hourly,
            currentHourIso = "2026-11-15T04"
        )

        assertEquals("Best outdoor window today: 08:00 (17°C)", window)
    }

    @Test
    fun `computeBestWorkoutWindow returns null when only sleeping hours fall inside horizon`() {
        val lateNightHourly = OpenMeteoHourly(
            time = listOf("2026-11-15T22:00", "2026-11-15T23:00"),
            temperatureC = listOf(15.0, 14.0)
        )

        val window = EnvironmentContextRepository.computeBestWorkoutWindow(
            hourly = lateNightHourly,
            currentHourIso = "2026-11-15T22"
        )

        assertNull(window)
    }

    @Test
    fun `resolveCurrentPrecipProb matches current hour prefix cleanly`() {
        val hourly = OpenMeteoHourly(
            time = listOf("2026-11-15T10:00", "2026-11-15T11:00", "2026-11-15T12:00"),
            precipitationProbabilityPct = listOf(10, 45, 75)
        )

        assertEquals(
            45,
            EnvironmentContextRepository.resolveCurrentPrecipProb(hourly, "2026-11-15T11:15")
        )
        assertEquals(
            0,
            EnvironmentContextRepository.resolveCurrentPrecipProb(null, "2026-11-15T11:00")
        )
    }

    @Test
    fun `cache freshness expires after 20 minutes or coordinate relocation`() {
        val baseTime = 1_700_000_000_000L
        val snapshot = EnvironmentSnapshot(
            fetchedAtMs = baseTime,
            latitude = 37.9838,
            longitude = 23.7275
        )

        // Within 10 min and < 0.03 deg -> fresh
        assertTrue(
            EnvironmentContextRepository.isCacheFresh(
                cached = snapshot,
                lat = 37.9900,
                lng = 23.7300,
                now = baseTime + 10 * 60_000L
            )
        )

        // Beyond 20 min TTL -> expired
        assertFalse(
            EnvironmentContextRepository.isCacheFresh(
                cached = snapshot,
                lat = 37.9838,
                lng = 23.7275,
                now = baseTime + 21 * 60_000L
            )
        )

        // Relocated > 0.03 deg -> stale
        assertFalse(
            EnvironmentContextRepository.isCacheFresh(
                cached = snapshot,
                lat = 38.0500,
                lng = 23.7275,
                now = baseTime + 5 * 60_000L
            )
        )
    }

    @Test
    fun `decodeWmoWeatherCode maps standard and unknown WMO codes`() {
        assertEquals("Clear", EnvironmentContextRepository.decodeWmoWeatherCode(0))
        assertEquals("Partly Cloudy", EnvironmentContextRepository.decodeWmoWeatherCode(2))
        assertEquals("Thunderstorm", EnvironmentContextRepository.decodeWmoWeatherCode(95))
        assertEquals("Outdoor", EnvironmentContextRepository.decodeWmoWeatherCode(999))
    }
}

