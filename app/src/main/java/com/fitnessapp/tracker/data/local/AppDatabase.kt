package com.fitnessapp.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitnessapp.tracker.data.local.dao.ChatMessageDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.dao.ChatSessionDao
import com.fitnessapp.tracker.data.local.dao.RoutineDao
import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.dao.TrainingPlanDao
import com.fitnessapp.tracker.data.local.entity.ChatMessageEntity
import com.fitnessapp.tracker.data.local.entity.ChatSessionEntity
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.RoutineEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.TrainingPlanEntity
import com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity
import com.fitnessapp.tracker.data.local.dao.PersonalRecordDao

@Database(
    entities = [
        UserEntity::class,
        WorkoutSessionEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        RoutineEntity::class,
        ChallengeEntity::class,
        TrainingPlanEntity::class,
        PersonalRecordEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(ChallengeTypeConverters::class, RoutineTypeConverters::class, PersonalRecordTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun routineDao(): RoutineDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun personalRecordDao(): PersonalRecordDao

    companion object {
        const val DATABASE_NAME = "cycling_tracker.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create the new chat_sessions table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                
                // Create a legacy session for existing messages
                val currentTime = System.currentTimeMillis()
                database.execSQL(
                    "INSERT INTO `chat_sessions` (`id`, `title`, `createdAt`) VALUES (1, 'Legacy Chat', $currentTime)"
                )

                // Add sessionId column to chat_messages with default 1
                database.execSQL(
                    "ALTER TABLE `chat_messages` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 1"
                )

                // Create index on the new column
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )

                // Recreate chat_messages table to add the foreign key constraint (SQLite ALTER TABLE limitation)
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sessionId` INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                database.execSQL(
                    "INSERT INTO `chat_messages_new` (`id`, `role`, `text`, `timestamp`, `sessionId`) SELECT `id`, `role`, `text`, `timestamp`, `sessionId` FROM `chat_messages`"
                )
                database.execSQL("DROP TABLE `chat_messages`")
                database.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `workout_sessions` ADD COLUMN `activityType` TEXT NOT NULL DEFAULT 'CYCLING'"
                )
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_routines` (`id` INTEGER NOT NULL, `interval` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `autoImprove` INTEGER NOT NULL, `autoImprovePercentage` REAL NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `lastCompletedPeriodEnd` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `chat_sessions` ADD COLUMN `activityType` TEXT NOT NULL DEFAULT 'CYCLING'"
                )
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_routines_new` (`activityType` TEXT NOT NULL, `interval` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `autoImprove` INTEGER NOT NULL, `autoImprovePercentage` REAL NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `lastCompletedPeriodEnd` INTEGER NOT NULL, PRIMARY KEY(`activityType`))"
                )
                database.execSQL(
                    "INSERT INTO `workout_routines_new` (`activityType`, `interval`, `metric`, `targetValue`, `autoImprove`, `autoImprovePercentage`, `currentPeriodStart`, `currentPeriodEnd`, `lastCompletedPeriodEnd`) SELECT 'CYCLING', `interval`, `metric`, `targetValue`, `autoImprove`, `autoImprovePercentage`, `currentPeriodStart`, `currentPeriodEnd`, `lastCompletedPeriodEnd` FROM `workout_routines`"
                )
                database.execSQL("DROP TABLE `workout_routines`")
                database.execSQL("ALTER TABLE `workout_routines_new` RENAME TO `workout_routines`")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `currentProgress` REAL NOT NULL, `period` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER)"
                )
                database.execSQL(
                    "ALTER TABLE `workout_sessions` ADD COLUMN `isChallengeCompletion` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `training_plan` (`id` INTEGER NOT NULL, `generatedAtMs` INTEGER NOT NULL, `planJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * No schema change — enums are stored as their String name, which matches
         * the existing raw String values already in the database.
         * Room requires a migration entry whenever the version is bumped.
         */
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No DDL changes — TypeConverters handle Kotlin ↔ String mapping transparently.
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personal_records` (`recordType` TEXT NOT NULL, `activityType` TEXT NOT NULL, `value` REAL NOT NULL, `sessionId` INTEGER NOT NULL, `achievedAt` INTEGER NOT NULL, PRIMARY KEY(`recordType`, `activityType`))"
                )
            }
        }
    }
}
