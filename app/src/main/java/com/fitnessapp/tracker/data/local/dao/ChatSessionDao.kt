package com.fitnessapp.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fitnessapp.tracker.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions WHERE activityType = :activityType ORDER BY createdAt DESC")
    fun getSessionsByActivity(activityType: String): Flow<List<ChatSessionEntity>>

    @Insert
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
