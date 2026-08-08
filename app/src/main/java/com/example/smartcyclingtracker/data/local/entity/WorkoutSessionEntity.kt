package com.example.smartcyclingtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val routePointsJson: String = "[]"      // JSON array of {lat, lng, alt, timestamp}
)
