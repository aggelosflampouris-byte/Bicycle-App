package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @androidx.room.ColumnInfo(defaultValue = "CYCLING")
    val activityType: String = "CYCLING",
    val createdAt: Long = System.currentTimeMillis()
)
