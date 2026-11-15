package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityType(
    val displayTitle: String,
    val nounSingular: String,
    val nounPlural: String,
    val longSessionThresholdMeters: Double,
    val fastSpeedThresholdKmh: Double,
    val defaultAvgSpeedKmh: Double
) {
    CYCLING(
        displayTitle = "Cycling",
        nounSingular = "ride",
        nounPlural = "rides",
        longSessionThresholdMeters = 30_000.0,
        fastSpeedThresholdKmh = 25.0,
        defaultAvgSpeedKmh = 22.0
    ),
    WALKING(
        displayTitle = "Walking",
        nounSingular = "walk",
        nounPlural = "walks",
        longSessionThresholdMeters = 5_000.0,
        fastSpeedThresholdKmh = 5.5,
        defaultAvgSpeedKmh = 5.0
    ),
    JOGGING(
        displayTitle = "Jogging",
        nounSingular = "jog",
        nounPlural = "jogs",
        longSessionThresholdMeters = 8_000.0,
        fastSpeedThresholdKmh = 10.0,
        defaultAvgSpeedKmh = 9.5
    );

    companion object {
        fun fromString(raw: String?): ActivityType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CYCLING
    }
}

/**
 * Room entity for a completed workout session.
 * Route GPS points stored as a JSON string to avoid additional table joins.
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = 0L,               // Unix epoch ms
    val endTime: Long = 0L,                 // Unix epoch ms
    val durationSeconds: Long = 0L,         // Active moving time
    val totalDistanceMeters: Double = 0.0,  // Total distance in metres
    val elevationGainMeters: Double = 0.0,  // Cumulative elevation gain
    val avgSpeedKmh: Double = 0.0,          // Average speed km/h
    val caloriesBurned: Double = 0.0,       // Calculated calories
    val wattsPerKg: Double = 0.0,           // Estimated power-to-weight ratio
    val routePointsJson: String = "[]",     // JSON array of {lat, lng, alt, timestamp}
    val activityType: String = ActivityType.CYCLING.name, // "CYCLING", "WALKING", or "JOGGING"
    val rpe: Int? = null,                   // 1..10 Rating of Perceived Exertion
    val wellnessFeel: Int? = null,          // 1..5 post-workout subjective feel
    val avgCadenceSpm: Double? = null,      // Sensor steps or pedal bounce cadence
    val gaitStabilityScore: Double? = null, // 0..100 accelerometer stability index
    val weatherTempC: Double? = null,       // Outdoor ambient temperature (C)
    val weatherWindKmh: Double? = null,     // Outdoor wind speed (km/h)
    val weatherHumidityPct: Double? = null, // Relative humidity (%)
    val environmentStressFactor: Double = 1.0, // Thermal + wind + elevation load modifier
    val perceivedNotes: String = ""         // Brief post-workout user note
)
