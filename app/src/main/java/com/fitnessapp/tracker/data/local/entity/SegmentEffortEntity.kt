package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "segment_efforts",
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["segmentId"]), Index(value = ["sessionId"])]
)
data class SegmentEffortEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val segmentId: Long,
    val sessionId: Long,
    val elapsedSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgWatts: Double = 0.0,
    val dateMs: Long = System.currentTimeMillis(),
    val isPr: Boolean = false
)
