package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.UserEntity
import kotlin.math.*

/**
 * Physics and Calorie Engine for cycling metrics.
 * All calculations run on Dispatchers.IO (called from the tracking service).
 */
object PhysicsEngine {

    // ── BMR (Mifflin-St Jeor Equation) ──────────────────────────────────────

    /**
     * Calculate Basal Metabolic Rate using Mifflin-St Jeor equation.
     * Men:   BMR = (10 × weight) + (6.25 × height) − (5 × age) + 5
     * Women: BMR = (10 × weight) + (6.25 × height) − (5 × age) − 161
     */
    fun calculateBMR(user: UserEntity): Double {
        val base = (10.0 * user.weightKg) + (6.25 * user.heightCm) - (5.0 * user.age)
        return if (user.gender.lowercase() == "male") base + 5.0 else base - 161.0
    }

    // ── MET (Metabolic Equivalent of Task) ──────────────────────────────────

    /**
     * Estimate MET value based on cycling speed (km/h).
     * Reference: Compendium of Physical Activities
     */
    fun estimateMET(speedKmh: Double, activityType: String = "CYCLING"): Double = when (activityType) {
        "WALKING" -> when {
            speedKmh < 3.2  -> 2.8    // Slow walking
            speedKmh < 4.8  -> 3.3    // Moderate walking
            speedKmh < 5.6  -> 4.3    // Brisk walking
            speedKmh < 6.4  -> 5.0    // Very brisk walking
            else             -> 6.0    // Fast walking
        }
        "JOGGING" -> when {
            speedKmh < 6.4  -> 6.0    // Jogging / walk combination
            speedKmh < 8.0  -> 8.3    // Jogging, general
            speedKmh < 9.7  -> 9.8    // Running, 6 mph
            speedKmh < 11.3 -> 11.0   // Running, 7 mph
            speedKmh < 12.9 -> 11.8   // Running, 8 mph
            else             -> 12.8   // Fast running
        }
        else -> when { // CYCLING
            speedKmh < 10.0  -> 4.0    // Very light cycling / casual
            speedKmh < 16.0  -> 6.0    // Light effort
            speedKmh < 19.0  -> 8.0    // Moderate effort
            speedKmh < 22.0  -> 10.0   // Vigorous effort
            speedKmh < 26.0  -> 12.0   // Racing / very vigorous
            else             -> 16.0   // High speed / competitive
        }
    }

    // ── Calorie Calculation ──────────────────────────────────────────────────

    /**
     * Calculate calories burned during active cycling time.
     * Formula: Calories = MET × weight (kg) × duration (hours)
     *
     * @param user             User biometrics
     * @param activeSeconds    Only active (non-paused) seconds
     * @param avgSpeedKmh      Average moving speed for MET estimation
     */
    fun calculateCalories(
        user: UserEntity,
        activeSeconds: Long,
        avgSpeedKmh: Double,
        activityType: String = "CYCLING"
    ): Double {
        val met = estimateMET(avgSpeedKmh, activityType)
        val hours = activeSeconds / 3600.0
        return met * user.weightKg * hours
    }

    // ── Power / Watts per kg ─────────────────────────────────────────────────

    /**
     * Estimate functional mechanical power output per kg (W/kg).
     *
     * Physiological formula:
     * 1 MET = 3.5 mL O2 / kg / min
     * 1 mL O2 ≈ 20.9 Joules (energy equivalent)
     * Metabolic Power Rate (W/kg) = (MET × 3.5 × 20.9) / 60.0 ≈ MET × 1.219 W/kg
     * Mechanical Power (W/kg) = Metabolic Power × Gross Efficiency (~20-22%)
     */
    fun calculateWattsPerKg(
        avgSpeedKmh: Double,
        user: UserEntity,
        activityType: String = "CYCLING"
    ): Double {
        if (avgSpeedKmh <= 0.0) return 0.0
        val met = estimateMET(avgSpeedKmh, activityType)
        val mechanicalEfficiency = when (activityType) {
            "WALKING" -> 0.18
            "JOGGING" -> 0.20
            else -> 0.22 // CYCLING
        }
        val metabolicWattsPerKg = (met * 3.5 * 20.9) / 60.0
        val wattsPerKg = metabolicWattsPerKg * mechanicalEfficiency
        return wattsPerKg.coerceIn(0.0, 10.0)
    }

    // ── Elevation Gain ───────────────────────────────────────────────────────

    /**
     * Calculate cumulative elevation gain from a list of altitude values.
     * Only counts positive altitude changes (ascents).
     */
    fun calculateElevationGain(altitudePoints: List<Double>): Double {
        if (altitudePoints.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until altitudePoints.size) {
            val delta = altitudePoints[i] - altitudePoints[i - 1]
            if (delta > 0) gain += delta
        }
        return gain
    }

    // ── Distance ─────────────────────────────────────────────────────────────

    /**
     * Calculate distance between two GPS coordinates using the Haversine formula.
     * Returns distance in metres.
     */
    fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    // ── Speed Formatting ─────────────────────────────────────────────────────

    fun metersPerSecondToKmh(mps: Double): Double = mps * 3.6

    fun formatSpeed(kmh: Double): String = "%.1f".format(kmh)

    fun formatDistance(metres: Double): String = when {
        metres < 1000 -> "${"%.0f".format(metres)} m"
        else          -> "${"%.2f".format(metres / 1000.0)} km"
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
