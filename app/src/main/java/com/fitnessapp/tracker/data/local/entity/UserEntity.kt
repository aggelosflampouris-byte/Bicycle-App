package com.fitnessapp.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing user biometric data.
 * Supports fallback defaults: 75kg, 175cm, 35yo if user skips onboarding.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val gender: String = "male",         // "male" or "female"
    val age: Int = 35,                   // Default 35 years
    val weightKg: Float = 75f,           // Default 75 kg
    val heightCm: Float = 175f,          // Default 175 cm
    val name: String = "Cyclist"
)
