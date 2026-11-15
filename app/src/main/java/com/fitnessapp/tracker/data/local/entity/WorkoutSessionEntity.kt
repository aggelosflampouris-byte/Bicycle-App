package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityType { CYCLING, WALKING, JOGGING }

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
    val isChallengeCompletion: Boolean = false, // Legacy challenge compatibility flag
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
