package com.fitnessapp.tracker.data.remote

import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.RoutineEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun syncWorkoutSession(session: WorkoutSessionEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("workout_sessions")
                .document(session.id.toString())
                .set(session)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
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
                .set(routine)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncChallenge(challenge: ChallengeEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("challenges")
                .document(challenge.id.toString())
                .set(challenge)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
