package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_routines")
data class RoutineEntity(
    @PrimaryKey val activityType: String = "CYCLING", // "CYCLING", "WALKING", "JOGGING"
    val interval: String = "WEEKLY", // "DAILY", "WEEKLY", "MONTHLY"
    val metric: String = "DISTANCE", // "CALORIES", "DISTANCE"
    val targetValue: Double = 0.0,
    val autoImprove: Boolean = false,
    val autoImprovePercentage: Double = 0.05, // e.g. 5%
    val currentPeriodStart: Long = 0L,
    val currentPeriodEnd: Long = 0L,
    val lastCompletedPeriodEnd: Long = 0L // To track when to apply auto-improve
)
