package com.example.smartcyclingtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_routines")
data class RoutineEntity(
    @PrimaryKey val id: Int = 1, // We only allow one active routine, so we hardcode ID to 1
    val interval: String, // "DAILY", "WEEKLY", "MONTHLY"
    val metric: String, // "CALORIES", "DISTANCE"
    val targetValue: Double,
    val autoImprove: Boolean,
    val autoImprovePercentage: Double = 0.05, // e.g. 5%
    val currentPeriodStart: Long = 0L,
    val currentPeriodEnd: Long = 0L,
    val lastCompletedPeriodEnd: Long = 0L // To track when to apply auto-improve
)
