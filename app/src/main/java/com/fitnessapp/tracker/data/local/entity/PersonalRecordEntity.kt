package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity

enum class PersonalRecordType(val displayName: String, val icon: String, val isTimeBased: Boolean) {
    FASTEST_1KM("Fastest 1 km", "⚡", true),
    FASTEST_5KM("Fastest 5 km", "🥇", true),
    FASTEST_10KM("Fastest 10 km", "🏆", true),
    FASTEST_20KM("Fastest 20 km", "👑", true),
    FASTEST_50KM("Fastest 50 km", "💎", true),
    LONGEST_DISTANCE("Longest Distance", "🚴", false),
    LONGEST_DURATION("Longest Duration", "⏱️", true),
    MAX_ELEVATION_GAIN("Highest Elevation", "⛰️", false),
    MAX_AVG_SPEED("Top Avg Speed", "🚀", false),
    MAX_CALORIES("Most Calories Burned", "🔥", false)
}

@Entity(
    tableName = "personal_records",
    primaryKeys = ["recordType", "activityType"]
)
data class PersonalRecordEntity(
    val recordType: PersonalRecordType = PersonalRecordType.FASTEST_1KM,
    val activityType: String = "CYCLING",
    val value: Double = 0.0, // Seconds for time-based, meters for distance/elevation, km/h for speed, kcal for calories
    val sessionId: Long = 0L,
    val achievedAt: Long = System.currentTimeMillis()
)
