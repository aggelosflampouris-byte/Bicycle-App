package com.fitnessapp.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.dao.RoutineDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.RoutineEntity
import com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity
import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.dao.PersonalRecordDao
import com.fitnessapp.tracker.data.local.dao.TwinSnapshotDao
import com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao

@Database(
    entities = [
        UserEntity::class,
        WorkoutSessionEntity::class,
        RoutineEntity::class,
        PersonalRecordEntity::class,
        WellnessCheckInEntity::class,
        TwinSnapshotEntity::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(
    RoutineTypeConverters::class,
    PersonalRecordTypeConverters::class,
    TwinTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun routineDao(): RoutineDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun wellnessCheckInDao(): WellnessCheckInDao
    abstract fun twinSnapshotDao(): TwinSnapshotDao

    companion object {
        const val DATABASE_NAME = "cycling_tracker.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create the new chat_sessions table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                
                // Create a legacy session for existing messages
                val currentTime = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `chat_sessions` (`id`, `title`, `createdAt`) VALUES (1, 'Legacy Chat', $currentTime)"
                )

                // Add sessionId column to chat_messages with default 1
                db.execSQL(
                    "ALTER TABLE `chat_messages` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 1"
                )

                // Create index on the new column
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )

                // Recreate chat_messages table to add the foreign key constraint (SQLite ALTER TABLE limitation)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sessionId` INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `chat_messages_new` (`id`, `role`, `text`, `timestamp`, `sessionId`) SELECT `id`, `role`, `text`, `timestamp`, `sessionId` FROM `chat_messages`"
                )
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `workout_sessions` ADD COLUMN `activityType` TEXT NOT NULL DEFAULT 'CYCLING'"
                )
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_routines` (`id` INTEGER NOT NULL, `interval` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `autoImprove` INTEGER NOT NULL, `autoImprovePercentage` REAL NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `lastCompletedPeriodEnd` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chat_sessions` ADD COLUMN `activityType` TEXT NOT NULL DEFAULT 'CYCLING'"
                )
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_routines_new` (`activityType` TEXT NOT NULL, `interval` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `autoImprove` INTEGER NOT NULL, `autoImprovePercentage` REAL NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `lastCompletedPeriodEnd` INTEGER NOT NULL, PRIMARY KEY(`activityType`))"
                )
                db.execSQL(
                    "INSERT INTO `workout_routines_new` (`activityType`, `interval`, `metric`, `targetValue`, `autoImprove`, `autoImprovePercentage`, `currentPeriodStart`, `currentPeriodEnd`, `lastCompletedPeriodEnd`) SELECT 'CYCLING', `interval`, `metric`, `targetValue`, `autoImprove`, `autoImprovePercentage`, `currentPeriodStart`, `currentPeriodEnd`, `lastCompletedPeriodEnd` FROM `workout_routines`"
                )
                db.execSQL("DROP TABLE `workout_routines`")
                db.execSQL("ALTER TABLE `workout_routines_new` RENAME TO `workout_routines`")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `currentProgress` REAL NOT NULL, `period` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER)"
                )
                db.execSQL(
                    "ALTER TABLE `workout_sessions` ADD COLUMN `isChallengeCompletion` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
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
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No DDL changes — TypeConverters handle Kotlin ↔ String mapping transparently.
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personal_records` (`recordType` TEXT NOT NULL, `activityType` TEXT NOT NULL, `value` REAL NOT NULL, `sessionId` INTEGER NOT NULL, `achievedAt` INTEGER NOT NULL, PRIMARY KEY(`recordType`, `activityType`))"
                )
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startLat` REAL NOT NULL, `startLng` REAL NOT NULL, `endLat` REAL NOT NULL, `endLng` REAL NOT NULL, `distanceMeters` REAL NOT NULL, `elevationGainMeters` REAL NOT NULL, `avgGradientPct` REAL NOT NULL, `activityType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `segment_efforts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `sessionId` INTEGER NOT NULL, `elapsedSeconds` INTEGER NOT NULL, `avgSpeedKmh` REAL NOT NULL, `maxSpeedKmh` REAL NOT NULL, `avgWatts` REAL NOT NULL, `dateMs` INTEGER NOT NULL, `isPr` INTEGER NOT NULL, FOREIGN KEY(`segmentId`) REFERENCES `segments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_segment_efforts_segmentId` ON `segment_efforts` (`segmentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_segment_efforts_sessionId` ON `segment_efforts` (`sessionId`)"
                )
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `segment_efforts`")
                db.execSQL("DROP TABLE IF EXISTS `segments`")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `rpe` INTEGER")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `wellnessFeel` INTEGER")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `avgCadenceSpm` REAL")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `gaitStabilityScore` REAL")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `weatherTempC` REAL")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `weatherWindKmh` REAL")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `weatherHumidityPct` REAL")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `environmentStressFactor` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `perceivedNotes` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wellness_check_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `sleepQuality` INTEGER NOT NULL, `energyLevel` INTEGER NOT NULL, `muscleSoreness` INTEGER NOT NULL, `stressLevel` INTEGER NOT NULL, `hydrationScore` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `twin_snapshots` (`dateKey` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `readinessScore` INTEGER NOT NULL, `bodyBattery` INTEGER NOT NULL, `acuteLoad` REAL NOT NULL, `chronicLoad` REAL NOT NULL, `acwr` REAL NOT NULL, `strainScore` REAL NOT NULL, `recommendedIntensity` TEXT NOT NULL, `weatherTempC` REAL, `windSpeedKmh` REAL, `humidityPct` REAL, `uvIndex` REAL, PRIMARY KEY(`dateKey`))"
                )
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `challenges`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_sessions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `totalDistanceMeters` REAL NOT NULL,
                        `elevationGainMeters` REAL NOT NULL,
                        `avgSpeedKmh` REAL NOT NULL,
                        `caloriesBurned` REAL NOT NULL,
                        `wattsPerKg` REAL NOT NULL,
                        `routePointsJson` TEXT NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `rpe` INTEGER,
                        `wellnessFeel` INTEGER,
                        `avgCadenceSpm` REAL,
                        `gaitStabilityScore` REAL,
                        `weatherTempC` REAL,
                        `weatherWindKmh` REAL,
                        `weatherHumidityPct` REAL,
                        `environmentStressFactor` REAL NOT NULL,
                        `perceivedNotes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `workout_sessions_new` (
                        `id`, `startTime`, `endTime`, `durationSeconds`, `totalDistanceMeters`,
                        `elevationGainMeters`, `avgSpeedKmh`, `caloriesBurned`, `wattsPerKg`,
                        `routePointsJson`, `activityType`, `rpe`, `wellnessFeel`, `avgCadenceSpm`,
                        `gaitStabilityScore`, `weatherTempC`, `weatherWindKmh`, `weatherHumidityPct`,
                        `environmentStressFactor`, `perceivedNotes`
                    )
                    SELECT
                        `id`, `startTime`, `endTime`, `durationSeconds`, `totalDistanceMeters`,
                        `elevationGainMeters`, `avgSpeedKmh`, `caloriesBurned`, `wattsPerKg`,
                        `routePointsJson`, `activityType`, `rpe`, `wellnessFeel`, `avgCadenceSpm`,
                        `gaitStabilityScore`, `weatherTempC`, `weatherWindKmh`, `weatherHumidityPct`,
                        COALESCE(`environmentStressFactor`, 1.0), COALESCE(`perceivedNotes`, '')
                    FROM `workout_sessions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `workout_sessions`")
                db.execSQL("ALTER TABLE `workout_sessions_new` RENAME TO `workout_sessions`")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Remove legacy AI Coach tables while keeping all Digital Twin, workout, and profile data intact.
                db.execSQL("DROP TABLE IF EXISTS `chat_messages`")
                db.execSQL("DROP TABLE IF EXISTS `chat_sessions`")
                db.execSQL("DROP TABLE IF EXISTS `training_plan`")
            }
        }
    }
}
