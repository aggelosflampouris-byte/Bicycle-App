package com.example.smartcyclingtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smartcyclingtracker.data.local.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plan WHERE id = 1 LIMIT 1")
    fun getPlanFlow(): Flow<TrainingPlanEntity?>

    @Query("SELECT * FROM training_plan WHERE id = 1 LIMIT 1")
    suspend fun getPlan(): TrainingPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TrainingPlanEntity)

    @Query("DELETE FROM training_plan")
    suspend fun deletePlan()
}
