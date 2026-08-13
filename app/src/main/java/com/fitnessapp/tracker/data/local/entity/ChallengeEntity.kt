package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChallengeStatus {
    PENDING, ACCEPTED, ACTIVE, COMPLETED, CANCELLED, DENIED
}

enum class ChallengeMetric {
    DISTANCE, SPEED, CALORIES
}

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityType: String, // CYCLING, WALKING, JOGGING
    val metric: String, // DISTANCE, SPEED, CALORIES
    val targetValue: Double,
    val currentProgress: Double = 0.0,
    val period: String, // DAY, WEEK, MONTH
    val status: String, // PENDING, ACCEPTED, ACTIVE, COMPLETED, CANCELLED, DENIED
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
