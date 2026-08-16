package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RoutineInterval {
    DAILY, WEEKLY, MONTHLY
}

enum class RoutineMetric {
    CALORIES, DISTANCE
}

@Entity(tableName = "workout_routines")
data class RoutineEntity(
    @PrimaryKey val activityType: String = "CYCLING", // "CYCLING", "WALKING", "JOGGING"
    val interval: RoutineInterval = RoutineInterval.WEEKLY,
    val metric: RoutineMetric = RoutineMetric.DISTANCE,
    val targetValue: Double = 0.0,
    val autoImprove: Boolean = false,
    val autoImprovePercentage: Double = 0.05, // e.g. 5%
    val currentPeriodStart: Long = 0L,
    val currentPeriodEnd: Long = 0L,
    val lastCompletedPeriodEnd: Long = 0L // To track when to apply auto-improve
)
