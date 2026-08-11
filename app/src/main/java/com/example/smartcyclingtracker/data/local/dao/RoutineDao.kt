package com.example.smartcyclingtracker.data.local.dao

import androidx.room.*
import com.example.smartcyclingtracker.data.local.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM workout_routines WHERE id = 1")
    fun getRoutineFlow(): Flow<RoutineEntity?>

    @Query("SELECT * FROM workout_routines WHERE id = 1")
    suspend fun getRoutine(): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRoutine(routine: RoutineEntity)

    @Query("DELETE FROM workout_routines")
    suspend fun clearRoutine()
}
