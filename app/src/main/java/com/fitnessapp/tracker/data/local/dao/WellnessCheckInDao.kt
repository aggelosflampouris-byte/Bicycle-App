package com.fitnessapp.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WellnessCheckInDao {
    @Query("SELECT * FROM wellness_check_ins ORDER BY timestampMs DESC LIMIT 1")
    fun getLatestCheckInFlow(): Flow<WellnessCheckInEntity?>

    @Query("SELECT * FROM wellness_check_ins ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentCheckInsFlow(limit: Int = 14): Flow<List<WellnessCheckInEntity>>

    @Query("SELECT * FROM wellness_check_ins ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentCheckIns(limit: Int = 14): List<WellnessCheckInEntity>

    @Query("SELECT * FROM wellness_check_ins ORDER BY timestampMs DESC")
    suspend fun getAllCheckIns(): List<WellnessCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: WellnessCheckInEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIns(checkIns: List<WellnessCheckInEntity>)
}

