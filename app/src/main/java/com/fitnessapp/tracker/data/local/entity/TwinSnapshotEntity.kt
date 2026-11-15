package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily Digital Twin snapshot capturing physiological readiness, load balance, and environment context.
 */
@Entity(tableName = "twin_snapshots")
data class TwinSnapshotEntity(
    @PrimaryKey
    val dateKey: String,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val readinessScore: Int = 75,
    val bodyBattery: Int = 75,
    val acuteLoad: Double = 0.0,
    val chronicLoad: Double = 0.0,
    val acwr: Double = 1.0,
    val strainScore: Double = 0.0,
    val recommendedIntensity: String = "MODERATE",
    val weatherTempC: Double? = null,
    val windSpeedKmh: Double? = null,
    val humidityPct: Double? = null,
    val uvIndex: Double? = null
)

