package com.fitnessapp.tracker.data.remote

import android.util.Log
import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.dao.RoutineDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.RoutineEntity
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val routineDao: RoutineDao,
    private val challengeDao: ChallengeDao,
    private val userDao: UserDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirestoreRepository"
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun syncWorkoutSession(session: WorkoutSessionEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        val docId = "${session.startTime}_${session.activityType}"
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("workout_sessions")
                .document(docId)
                .set(session, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync workout session: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncRoutine(routine: RoutineEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("routines")
                .document(routine.activityType)
                .set(routine, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync routine: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncChallenge(challenge: ChallengeEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        val docId = challenge.createdAt.toString()
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("challenges")
                .document(docId)
                .set(challenge, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync challenge: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncUserProfile(user: UserEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("profile")
                .document("profile_data")
                .set(user, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Pulls and restores all cloud data (profile, workouts, routines, challenges)
     * for the logged-in user into the local Room database, deduplicating records.
     */
    suspend fun pullAndRestoreUserData(): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            // 1. Restore Profile Data
            val profileDoc = firestore.collection("users")
                .document(uid)
                .collection("profile")
                .document("profile_data")
                .get()
                .await()

            if (profileDoc.exists()) {
                val cloudUser = profileDoc.toObject(UserEntity::class.java)
                if (cloudUser != null) {
                    val existing = userDao.getUser()
                    userDao.upsertUser(cloudUser.copy(id = existing?.id ?: 0))
                }
            }

            // 2. Restore Workout Sessions
            val sessionsSnapshot = firestore.collection("users")
                .document(uid)
                .collection("workout_sessions")
                .get()
                .await()

            val cloudSessions = sessionsSnapshot.toObjects(WorkoutSessionEntity::class.java)
            if (cloudSessions.isNotEmpty()) {
                val localSessions = workoutSessionDao.getRecentSessions(10000)
                val existingStartTimes = localSessions.map { it.startTime }.toSet()
                val newSessions = cloudSessions.filter { it.startTime !in existingStartTimes }
                    .map { it.copy(id = 0) } // Reset ID so Room auto-generates primary key
                if (newSessions.isNotEmpty()) {
                    workoutSessionDao.insertSessions(newSessions)
                }
            }

            // 3. Restore Routines
            val routinesSnapshot = firestore.collection("users")
                .document(uid)
                .collection("routines")
                .get()
                .await()

            val cloudRoutines = routinesSnapshot.toObjects(RoutineEntity::class.java)
            for (routine in cloudRoutines) {
                routineDao.saveRoutine(routine)
            }

            // 4. Restore Challenges
            val challengesSnapshot = firestore.collection("users")
                .document(uid)
                .collection("challenges")
                .get()
                .await()

            val cloudChallenges = challengesSnapshot.toObjects(ChallengeEntity::class.java)
            if (cloudChallenges.isNotEmpty()) {
                val localChallenges = challengeDao.getAllChallenges()
                val existingCreatedAts = localChallenges.map { it.createdAt }.toSet()
                val newChallenges = cloudChallenges.filter { it.createdAt !in existingCreatedAts }
                    .map { it.copy(id = 0) }
                for (ch in newChallenges) {
                    challengeDao.insertChallenge(ch)
                }
            }

            Log.d(TAG, "Successfully restored cloud user data")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring user cloud data: ${e.message}", e)
            Result.failure(e)
        }
    }
}
