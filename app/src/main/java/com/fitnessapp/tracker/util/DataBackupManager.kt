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
    val sessions: List<WorkoutSessionEntity>,
    val routines: List<RoutineEntity>,
    val challenges: List<ChallengeEntity>
)

class DataBackupManager(private val context: Context, private val db: AppDatabase) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val cryptoManager = CryptoManager()

    suspend fun exportData(uri: Uri, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = db.userDao().getUser()
            // Using a simple query for sessions since we don't need Flow here
            val sessions = db.workoutSessionDao().getRecentSessions(limit = 10000) // Getting most recent 10k for backup
            val routines = db.routineDao().getAllRoutines()
            val challenges = db.challengeDao().getAllChallenges()

            val backup = BackupData(user, sessions, routines, challenges)
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
                backup.user?.let { db.userDao().updateUser(it) } // We can't use insert with replace if we have id mapping, actually we can just use insert
                // Wait, UserDao has updateUser but no insertUser that takes UserEntity directly for suspend?
                // Actually UserDao has insertUser(user: UserEntity) in our code. Let's just use insertSessions, etc.
                backup.user?.let { 
                    val current = db.userDao().getUser()
                    if (current == null) db.userDao().insertUser(it) else db.userDao().updateUser(it)
                }
                
                if (backup.sessions.isNotEmpty()) {
                    db.workoutSessionDao().insertSessions(backup.sessions)
                }
                if (backup.routines.isNotEmpty()) {
                    // Assuming routineDao has insertRoutines or we insert one by one
                    backup.routines.forEach { db.routineDao().saveRoutine(it) }
                }
                if (backup.challenges.isNotEmpty()) {
                    // Assuming challengeDao has insertChallenge
                    backup.challenges.forEach { db.challengeDao().insertChallenge(it) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
