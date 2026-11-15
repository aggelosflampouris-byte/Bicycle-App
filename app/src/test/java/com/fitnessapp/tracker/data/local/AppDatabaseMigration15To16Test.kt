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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies Room v15 -> v16 migration drops legacy AI Coach tables (`chat_messages`,
 * `chat_sessions`, `training_plan`) while preserving athlete profile, workouts, PRs,
 * and Digital Twin check-ins/snapshots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppDatabaseMigration15To16Test {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var roomDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("migration_15_16_test.db")
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
    fun migrate15To16_dropsAiCoachTablesAndPreservesDigitalTwinData() = runTest {
        createV15DatabaseWithAiCoachTables()

        val migratedDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbFile.name
        )
            .addMigrations(AppDatabase.MIGRATION_15_16)
            .allowMainThreadQueries()
            .build()
        roomDb = migratedDb

        // Executing queries triggers MIGRATION_15_16 and Room's onValidateSchema()
        val user = migratedDb.userDao().getUser()
        assertNotNull(user)
        assertEquals("Athlete", user?.name)

        val sessions = migratedDb.workoutSessionDao().getAllSessionsForBackup()
        assertEquals(1, sessions.size)
        assertEquals(303L, sessions.first().id)
        assertEquals(18_250.0, sessions.first().totalDistanceMeters, 0.001)

        val checkIns = migratedDb.wellnessCheckInDao().getRecentCheckIns(5)
        assertEquals(1, checkIns.size)
        assertEquals(5, checkIns.first().sleepQuality)

        val snapshots = migratedDb.twinSnapshotDao().getAllSnapshots()
        assertEquals(1, snapshots.size)
        assertEquals(84, snapshots.first().readinessScore)

        val sqliteDb = migratedDb.openHelper.writableDatabase
        listOf("chat_messages", "chat_sessions", "training_plan").forEach { droppedTable ->
            sqliteDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(droppedTable)
            ).use { cursor ->
                assertFalse("$droppedTable table must be dropped in v16", cursor.moveToFirst())
            }
        }
        sqliteDb.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_chat_messages_sessionId'"
        ).use { cursor ->
            assertFalse("chat_messages index must be dropped in v16", cursor.moveToFirst())
        }
    }

    private fun createV15DatabaseWithAiCoachTables() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
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
                        "INSERT INTO `chat_sessions` (`id`, `title`, `activityType`, `createdAt`) VALUES (1, 'Legacy Session', 'CYCLING', 1700000000000)"
                    )
                    db.execSQL(
                        "INSERT INTO `chat_messages` (`id`, `role`, `text`, `timestamp`, `sessionId`) VALUES (1, 'user', 'How am I doing?', 1700000001000, 1)"
                    )
                    db.execSQL(
                        "INSERT INTO `training_plan` (`id`, `generatedAtMs`, `planJson`) VALUES (1, 1700000000000, '[]')"
                    )
                    db.execSQL(
                        "INSERT INTO `users` (`id`, `gender`, `age`, `weightKg`, `heightCm`, `name`) VALUES (1, 'Male', 30, 74.0, 180.0, 'Athlete')"
                    )
                    db.execSQL(
                        """
                        INSERT INTO `workout_sessions` (
                            `id`, `startTime`, `endTime`, `durationSeconds`, `totalDistanceMeters`,
                            `elevationGainMeters`, `avgSpeedKmh`, `caloriesBurned`, `wattsPerKg`,
                            `routePointsJson`, `activityType`, `rpe`, `wellnessFeel`,
                            `avgCadenceSpm`, `gaitStabilityScore`, `weatherTempC`, `weatherWindKmh`,
                            `weatherHumidityPct`, `environmentStressFactor`, `perceivedNotes`
                        ) VALUES (
                            303, 1700200000000, 1700203600000, 3600, 18250.0,
                            210.0, 18.25, 540.0, 2.5,
                            '[]', 'CYCLING', 6, 4,
                            86.0, 95.0, 21.0, 9.0,
                            52.0, 1.03, 'Smooth aerobic spin'
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO `wellness_check_ins` (`id`, `timestampMs`, `type`, `sleepQuality`, `energyLevel`, `muscleSoreness`, `stressLevel`, `hydrationScore`, `notes`) VALUES (1, 1700190000000, 'MORNING', 5, 4, 2, 2, 4, 'Rested')"
                    )
                    db.execSQL(
                        "INSERT INTO `twin_snapshots` (`dateKey`, `updatedAtMs`, `readinessScore`, `bodyBattery`, `acuteLoad`, `chronicLoad`, `acwr`, `strainScore`, `recommendedIntensity`, `weatherTempC`, `windSpeedKmh`, `humidityPct`, `uvIndex`) VALUES ('2023-11-17', 1700203600000, 84, 81, 52.0, 49.0, 1.06, 48.0, 'TEMPO', 21.0, 9.0, 52.0, 3.5)"
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
            helper.writableDatabase.close()
        }
    }
}

