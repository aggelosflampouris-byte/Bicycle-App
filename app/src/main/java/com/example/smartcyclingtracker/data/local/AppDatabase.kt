package com.example.smartcyclingtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [UserEntity::class, WorkoutSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        const val DATABASE_NAME = "cycling_tracker.db"
    }
}
