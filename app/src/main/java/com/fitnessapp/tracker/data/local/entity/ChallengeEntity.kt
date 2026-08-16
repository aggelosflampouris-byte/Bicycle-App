package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChallengeStatus {
    PENDING, ACCEPTED, ACTIVE, COMPLETED, CANCELLED, DENIED
}

enum class ChallengeMetric {
    DISTANCE, SPEED, CALORIES
}

enum class ChallengePeriod {
    DAY, WEEK, MONTH
}

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityType: String = "CYCLING",          // CYCLING, WALKING, JOGGING
    val metric: ChallengeMetric = ChallengeMetric.DISTANCE,
    val targetValue: Double = 0.0,
    val currentProgress: Double = 0.0,
    val period: ChallengePeriod = ChallengePeriod.DAY,
    val status: ChallengeStatus = ChallengeStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
