package com.fitnessapp.tracker.data.local.dao

import androidx.room.*
import com.fitnessapp.tracker.data.local.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM workout_routines WHERE activityType = :activityType")
    fun getRoutineFlow(activityType: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM workout_routines WHERE activityType = :activityType")
    suspend fun getRoutine(activityType: String): RoutineEntity?

    @Query("SELECT * FROM workout_routines")
    suspend fun getAllRoutines(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRoutine(routine: RoutineEntity)

    @Query("DELETE FROM workout_routines WHERE activityType = :activityType")
    suspend fun clearRoutine(activityType: String)
}
