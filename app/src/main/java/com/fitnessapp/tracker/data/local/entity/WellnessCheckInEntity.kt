package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CheckInType {
    DAILY,
    PRE_WORKOUT,
    POST_WORKOUT
}

/**
 * Room entity capturing subjective wellness & readiness signals reported by the user.
 */
@Entity(tableName = "wellness_check_ins")
data class WellnessCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val type: CheckInType = CheckInType.DAILY,
    val sleepQuality: Int = 3,    // 1..5
    val energyLevel: Int = 3,     // 1..5
    val muscleSoreness: Int = 2,  // 1 (fresh)..5 (very sore)
    val stressLevel: Int = 2,     // 1 (calm)..5 (high stress)
    val hydrationScore: Int = 3,  // 1..5
    val notes: String = ""
)

