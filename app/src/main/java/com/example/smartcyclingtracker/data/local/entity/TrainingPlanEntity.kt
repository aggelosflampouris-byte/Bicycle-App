package com.example.smartcyclingtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_plan")
data class TrainingPlanEntity(
    @PrimaryKey
    val id: Int = 1,
    val generatedAtMs: Long,
    val planJson: String
)

data class DailyPlan(
    val day: String,
    val title: String,
    val description: String,
    val targetDistance: Double?
)
