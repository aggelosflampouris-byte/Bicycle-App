package com.fitnessapp.tracker.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies the Room v13 -> v14 schema migration against Android's real SQLite engine
 * and Room's compiled schema validator (`onValidateSchema`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppDatabaseMigration13To14Test {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var roomDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("migration_13_14_test.db")
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
    fun migrate13To14_preservesLegacySessionAndPassesRoomSchemaValidation() = runTest {
        createV13DatabaseWithLegacyData()

        val migratedDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbFile.name
        )
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        roomDb = migratedDb

        // Opening and querying forces Room to run MIGRATION_13_14 and onValidateSchema()
        val sessions = migratedDb.workoutSessionDao().getAllSessionsForBackup()
        assertEquals(1, sessions.size)

        val migratedSession = sessions.first()
        assertEquals(101L, migratedSession.id)
        assertEquals(5200.0, migratedSession.totalDistanceMeters, 0.001)
        assertEquals("WALKING", migratedSession.activityType)
        assertNull(migratedSession.rpe)
        assertNull(migratedSession.wellnessFeel)
        assertNull(migratedSession.avgCadenceSpm)
        assertNull(migratedSession.gaitStabilityScore)
        assertNull(migratedSession.weatherTempC)
        assertNull(migratedSession.weatherWindKmh)
        assertNull(migratedSession.weatherHumidityPct)
        assertEquals(1.0, migratedSession.environmentStressFactor, 0.0001)
        assertEquals("", migratedSession.perceivedNotes)

        val user = migratedDb.userDao().getUser()
        assertNotNull(user)
        assertEquals("Runner", user?.name)
    }

    @Test
    fun migrate13To14_supportsReadWriteOnNewDigitalTwinTablesAndFeedback() = runTest {
        createV13DatabaseWithLegacyData()

        val migratedDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbFile.name
        )
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        roomDb = migratedDb

        migratedDb.workoutSessionDao().updatePostWorkoutFeedback(
            sessionId = 101L,
            rpe = 7,
            wellnessFeel = 4,
            perceivedNotes = "Headwind final 2km"
        )
        val updatedSession = migratedDb.workoutSessionDao().getSessionById(101L)
        assertNotNull(updatedSession)
        assertEquals(7, updatedSession?.rpe)
        assertEquals(4, updatedSession?.wellnessFeel)
        assertEquals("Headwind final 2km", updatedSession?.perceivedNotes)

        val checkInId = migratedDb.wellnessCheckInDao().insertCheckIn(
            WellnessCheckInEntity(
                timestampMs = 1_700_000_000_000L,
                type = CheckInType.DAILY,
                sleepQuality = 4,
                energyLevel = 5,
                muscleSoreness = 2,
                stressLevel = 2,
                hydrationScore = 4,
                notes = "Ready for threshold run"
            )
        )
        assertEquals(true, checkInId > 0L)
        val checkIns = migratedDb.wellnessCheckInDao().getAllCheckIns()
        assertEquals(1, checkIns.size)
        assertEquals(CheckInType.DAILY, checkIns.first().type)
        assertEquals("Ready for threshold run", checkIns.first().notes)

        migratedDb.twinSnapshotDao().upsertSnapshot(
            TwinSnapshotEntity(
                dateKey = "2026-11-15",
                updatedAtMs = 1_700_000_050_000L,
                readinessScore = 84,
                bodyBattery = 88,
                acuteLoad = 210.0,
                chronicLoad = 190.0,
                acwr = 1.11,
                strainScore = 11.4,
                recommendedIntensity = "TEMPO",
                weatherTempC = 17.5,
                windSpeedKmh = 9.0,
                humidityPct = 58.0,
                uvIndex = 3.2
            )
        )
        val snapshots = migratedDb.twinSnapshotDao().getAllSnapshots()
        assertEquals(1, snapshots.size)
        assertEquals("2026-11-15", snapshots.first().dateKey)
        assertEquals(84, snapshots.first().readinessScore)
        assertEquals("TEMPO", snapshots.first().recommendedIntensity)
    }

    private fun createV13DatabaseWithLegacyData() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gender` TEXT NOT NULL, `age` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `name` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `totalDistanceMeters` REAL NOT NULL, `elevationGainMeters` REAL NOT NULL, `avgSpeedKmh` REAL NOT NULL, `caloriesBurned` REAL NOT NULL, `wattsPerKg` REAL NOT NULL, `routePointsJson` TEXT NOT NULL, `activityType` TEXT NOT NULL, `isChallengeCompletion` INTEGER NOT NULL)"
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
                        "INSERT INTO `users` (`id`, `gender`, `age`, `weightKg`, `heightCm`, `name`) VALUES (1, 'male', 30, 72.5, 178.0, 'Runner')"
                    )
                    db.execSQL(
                        "INSERT INTO `workout_sessions` (`id`, `startTime`, `endTime`, `durationSeconds`, `totalDistanceMeters`, `elevationGainMeters`, `avgSpeedKmh`, `caloriesBurned`, `wattsPerKg`, `routePointsJson`, `activityType`, `isChallengeCompletion`) VALUES (101, 1700000000000, 1700003600000, 3600, 5200.0, 45.0, 5.2, 280.0, 1.4, '[]', 'WALKING', 0)"
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

