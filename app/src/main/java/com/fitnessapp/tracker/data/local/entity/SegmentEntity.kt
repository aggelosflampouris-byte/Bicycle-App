package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Double,
    val elevationGainMeters: Double = 0.0,
    val avgGradientPct: Double = 0.0,
    val activityType: String = "CYCLING",
    val createdAt: Long = System.currentTimeMillis()
)
