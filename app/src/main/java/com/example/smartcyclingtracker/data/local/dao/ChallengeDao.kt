package com.example.smartcyclingtracker.data.local.dao

import androidx.room.*
import com.example.smartcyclingtracker.data.local.entity.ChallengeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity): Long

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    @Query("SELECT * FROM challenges ORDER BY id DESC LIMIT 1")
    fun getLatestChallengeFlow(): Flow<ChallengeEntity?>
    
    @Query("SELECT * FROM challenges ORDER BY id DESC LIMIT 1")
    suspend fun getLatestChallenge(): ChallengeEntity?

    @Query("SELECT * FROM challenges WHERE status IN ('ACCEPTED', 'ACTIVE') ORDER BY id DESC LIMIT 1")
    suspend fun getActiveChallenge(): ChallengeEntity?

    @Query("DELETE FROM challenges")
    suspend fun deleteAll()

    @Query("SELECT * FROM challenges")
    suspend fun getAllChallenges(): List<ChallengeEntity>
}
