package com.fitnessapp.tracker.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies Room v14 -> v15 migration drops legacy Challenge schema while preserving
 * workout sessions and Digital Twin telemetry columns, passing Room schema validation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppDatabaseMigration14To15Test {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var roomDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("migration_14_15_test.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        roomDb?.close()
        if (dbFile.exists()) dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }

    @Test
    fun migrate14To15_dropsChallengesAndPreservesSessionsAndTwinColumns() = runTest {
        createV14DatabaseWithLegacyChallenges()

        val migratedDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbFile.name
        )
            .addMigrations(AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16)
            .allowMainThreadQueries()
            .build()
        roomDb = migratedDb

        // Opening and querying executes MIGRATION_14_15 and Room's onValidateSchema()
        val sessions = migratedDb.workoutSessionDao().getAllSessionsForBackup()
        assertEquals(1, sessions.size)

        val session = sessions.first()
        assertEquals(202L, session.id)
        assertEquals(12_400.0, session.totalDistanceMeters, 0.001)
        assertEquals("JOGGING", session.activityType)
        assertEquals(8, session.rpe)
        assertEquals(4, session.wellnessFeel)
        assertEquals(172.0, session.avgCadenceSpm ?: 0.0, 0.01)
        assertEquals(91.5, session.gaitStabilityScore ?: 0.0, 0.01)
        assertEquals(26.5, session.weatherTempC ?: 0.0, 0.01)
        assertEquals(14.0, session.weatherWindKmh ?: 0.0, 0.01)
        assertEquals(60.0, session.weatherHumidityPct ?: 0.0, 0.01)
        assertEquals(1.18, session.environmentStressFactor, 0.001)
        assertEquals("Hill repeats felt strong", session.perceivedNotes)

        val sqliteDb = migratedDb.openHelper.writableDatabase
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='challenges'").use { cursor ->
            assertFalse("challenges table must be dropped in v15", cursor.moveToFirst())
        }

        val sessionColumns = mutableSetOf<String>()
        sqliteDb.query("PRAGMA table_info('workout_sessions')").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                sessionColumns += cursor.getString(nameIdx)
            }
        }
        assertFalse("isChallengeCompletion column must be removed in v15", sessionColumns.contains("isChallengeCompletion"))
        assertTrue(sessionColumns.contains("rpe"))
        assertTrue(sessionColumns.contains("environmentStressFactor"))
    }

    private fun createV14DatabaseWithLegacyChallenges() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gender` TEXT NOT NULL, `age` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `name` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `workout_sessions` (
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
                            `isChallengeCompletion` INTEGER NOT NULL,
                            `rpe` INTEGER,
                            `wellnessFeel` INTEGER,
                            `avgCadenceSpm` REAL,
                            `gaitStabilityScore` REAL,
                            `weatherTempC` REAL,
                            `weatherWindKmh` REAL,
                            `weatherHumidityPct` REAL,
                            `environmentStressFactor` REAL NOT NULL DEFAULT 1.0,
                            `perceivedNotes` TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sessionId` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `activityType` TEXT NOT NULL DEFAULT 'CYCLING', `createdAt` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `workout_routines` (`activityType` TEXT NOT NULL, `interval` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `autoImprove` INTEGER NOT NULL, `autoImprovePercentage` REAL NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `lastCompletedPeriodEnd` INTEGER NOT NULL, PRIMARY KEY(`activityType`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityType` TEXT NOT NULL, `metric` TEXT NOT NULL, `targetValue` REAL NOT NULL, `currentProgress` REAL NOT NULL, `period` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `training_plan` (`id` INTEGER NOT NULL, `generatedAtMs` INTEGER NOT NULL, `planJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `personal_records` (`recordType` TEXT NOT NULL, `activityType` TEXT NOT NULL, `value` REAL NOT NULL, `sessionId` INTEGER NOT NULL, `achievedAt` INTEGER NOT NULL, PRIMARY KEY(`recordType`, `activityType`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `wellness_check_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `sleepQuality` INTEGER NOT NULL, `energyLevel` INTEGER NOT NULL, `muscleSoreness` INTEGER NOT NULL, `stressLevel` INTEGER NOT NULL, `hydrationScore` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `twin_snapshots` (`dateKey` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `readinessScore` INTEGER NOT NULL, `bodyBattery` INTEGER NOT NULL, `acuteLoad` REAL NOT NULL, `chronicLoad` REAL NOT NULL, `acwr` REAL NOT NULL, `strainScore` REAL NOT NULL, `recommendedIntensity` TEXT NOT NULL, `weatherTempC` REAL, `windSpeedKmh` REAL, `humidityPct` REAL, `uvIndex` REAL, PRIMARY KEY(`dateKey`))"
                    )

                    db.execSQL(
                        "INSERT INTO `challenges` (`id`, `activityType`, `metric`, `targetValue`, `currentProgress`, `period`, `status`, `createdAt`, `completedAt`) VALUES (1, 'JOGGING', 'DISTANCE', 10000.0, 10000.0, 'DAILY', 'COMPLETED', 1700000000000, 1700003600000)"
                    )
                    db.execSQL(
                        """
                        INSERT INTO `workout_sessions` (
                            `id`, `startTime`, `endTime`, `durationSeconds`, `totalDistanceMeters`,
                            `elevationGainMeters`, `avgSpeedKmh`, `caloriesBurned`, `wattsPerKg`,
                            `routePointsJson`, `activityType`, `isChallengeCompletion`,
                            `rpe`, `wellnessFeel`, `avgCadenceSpm`, `gaitStabilityScore`,
                            `weatherTempC`, `weatherWindKmh`, `weatherHumidityPct`,
                            `environmentStressFactor`, `perceivedNotes`
                        ) VALUES (
                            202, 1700100000000, 1700103600000, 3600, 12400.0,
                            180.0, 12.4, 720.0, 0.0,
                            '[]', 'JOGGING', 1,
                            8, 4, 172.0, 91.5,
                            26.5, 14.0, 60.0,
                            1.18, 'Hill repeats felt strong'
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        factory.create(config).use { helper ->
            helper.writableDatabase
        }
    }
}

