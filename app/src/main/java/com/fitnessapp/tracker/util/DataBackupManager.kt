package com.fitnessapp.tracker.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fitnessapp.tracker.data.local.AppDatabase
import com.fitnessapp.tracker.data.local.entity.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupData(
    val user: UserEntity?,
    val sessions: List<WorkoutSessionEntity>?,
    val routines: List<RoutineEntity>?,
    val challenges: List<ChallengeEntity>?,
    val personalRecords: List<PersonalRecordEntity>? = null,
    val trainingPlan: TrainingPlanEntity? = null
)

class DataBackupManager(private val context: Context, private val db: AppDatabase) {

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private val cryptoManager = CryptoManager()

    suspend fun exportData(uri: Uri, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = db.userDao().getUser()
            // Query complete sessions with full encrypted route points preserved
            val sessions = db.workoutSessionDao().getAllSessionsForBackup()
            val routines = db.routineDao().getAllRoutines()
            val challenges = db.challengeDao().getAllChallenges()
            val personalRecords = db.personalRecordDao().getAllRecords()
            val trainingPlan = db.trainingPlanDao().getPlan()

            val backup = BackupData(
                user = user,
                sessions = sessions,
                routines = routines,
                challenges = challenges,
                personalRecords = personalRecords,
                trainingPlan = trainingPlan
            )
            val json = gson.toJson(backup)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                cryptoManager.encrypt(jsonBytes, outputStream, password)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importData(uri: Uri, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var json = ""
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val decryptedBytes = cryptoManager.decrypt(inputStream, password)
                json = String(decryptedBytes, Charsets.UTF_8)
            } ?: return@withContext Result.failure(Exception("Could not open file"))

            val backup = gson.fromJson(json, BackupData::class.java)

            db.withTransaction {
                backup.user?.let { 
                    val current = db.userDao().getUser()
                    if (current == null) db.userDao().insertUser(it) else db.userDao().updateUser(it)
                }
                
                if (!backup.sessions.isNullOrEmpty()) {
                    db.workoutSessionDao().insertSessions(backup.sessions)
                }
                if (!backup.routines.isNullOrEmpty()) {
                    backup.routines.forEach { db.routineDao().saveRoutine(it) }
                }
                if (!backup.challenges.isNullOrEmpty()) {
                    backup.challenges.forEach { db.challengeDao().insertChallenge(it) }
                }
                if (!backup.personalRecords.isNullOrEmpty()) {
                    db.personalRecordDao().insertOrUpdateRecords(backup.personalRecords)
                }
                backup.trainingPlan?.let {
                    db.trainingPlanDao().insertPlan(it)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
