package com.fitnessapp.tracker.data.local.dao

import androidx.room.*
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Query("""
        SELECT id, startTime, endTime, durationSeconds, totalDistanceMeters,
               elevationGainMeters, avgSpeedKmh, caloriesBurned, wattsPerKg,
               '' as routePointsJson, activityType,
               rpe, wellnessFeel, avgCadenceSpm, gaitStabilityScore,
               weatherTempC, weatherWindKmh, weatherHumidityPct,
               environmentStressFactor, perceivedNotes
        FROM workout_sessions
        ORDER BY startTime DESC
    """)
    fun getAllSessionsFlow(): Flow<List<WorkoutSessionEntity>>

    @Query("""
        SELECT id, startTime, endTime, durationSeconds, totalDistanceMeters,
               elevationGainMeters, avgSpeedKmh, caloriesBurned, wattsPerKg,
               '' as routePointsJson, activityType,
               rpe, wellnessFeel, avgCadenceSpm, gaitStabilityScore,
               weatherTempC, weatherWindKmh, weatherHumidityPct,
               environmentStressFactor, perceivedNotes
        FROM workout_sessions
        ORDER BY startTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentSessions(limit: Int = 5): List<WorkoutSessionEntity>

    @Query("""
        SELECT id, startTime, endTime, durationSeconds, totalDistanceMeters,
               elevationGainMeters, avgSpeedKmh, caloriesBurned, wattsPerKg,
               '' as routePointsJson, activityType,
               rpe, wellnessFeel, avgCadenceSpm, gaitStabilityScore,
               weatherTempC, weatherWindKmh, weatherHumidityPct,
               environmentStressFactor, perceivedNotes
        FROM workout_sessions
        WHERE activityType = :activityType
        ORDER BY startTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentSessionsByType(activityType: String, limit: Int = 10): List<WorkoutSessionEntity>

    @Query("""
        UPDATE workout_sessions
        SET rpe = :rpe, wellnessFeel = :wellnessFeel, perceivedNotes = :perceivedNotes
        WHERE id = :sessionId
    """)
    suspend fun updatePostWorkoutFeedback(
        sessionId: Long,
        rpe: Int?,
        wellnessFeel: Int?,
        perceivedNotes: String
    )

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsForBackup(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("""
        SELECT SUM(totalDistanceMeters) as totalDist,
               AVG(avgSpeedKmh) as avgSpeed,
               SUM(caloriesBurned) as totalCals,
               COUNT(*) as sessionCount
        FROM workout_sessions
        WHERE startTime >= :startTime AND startTime <= :endTime AND activityType = :activityType
    """)
    fun getAggregateSummaryForPeriodFlow(startTime: Long, endTime: Long, activityType: String): Flow<AggregateSummary?>

    @Query("""
        SELECT SUM(totalDistanceMeters) as totalDist,
               AVG(avgSpeedKmh) as avgSpeed,
               SUM(caloriesBurned) as totalCals,
               COUNT(*) as sessionCount
        FROM workout_sessions
        WHERE startTime >= :startTime AND startTime <= :endTime AND activityType = :activityType
    """)
    suspend fun getAggregateSummaryForPeriod(startTime: Long, endTime: Long, activityType: String): AggregateSummary?

    data class AggregateSummary(
        val totalDist: Double?,
        val avgSpeed: Double?,
        val totalCals: Double?,
        val sessionCount: Int
    )
}
