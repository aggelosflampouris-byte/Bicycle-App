package com.example.smartcyclingtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.smartcyclingtracker.data.local.dao.ChatMessageDao
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.ChatMessageEntity
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [UserEntity::class, WorkoutSessionEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        const val DATABASE_NAME = "cycling_tracker.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }
    }
}
