package com.fitnessapp.tracker.data.local.dao

import androidx.room.*
import com.fitnessapp.tracker.data.local.entity.SegmentEffortEntity
import com.fitnessapp.tracker.data.local.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity): Long

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Delete
    suspend fun deleteSegment(segment: SegmentEntity)

    @Query("SELECT * FROM segments WHERE activityType = :activityType ORDER BY createdAt DESC")
    fun getSegmentsByActivity(activityType: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments ORDER BY createdAt DESC")
    fun getAllSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments")
    suspend fun getAllSegmentsList(): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE id = :id LIMIT 1")
    suspend fun getSegmentById(id: Long): SegmentEntity?

    // ── Efforts ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEffort(effort: SegmentEffortEntity): Long

    @Query("SELECT * FROM segment_efforts WHERE segmentId = :segmentId ORDER BY elapsedSeconds ASC")
    fun getLeaderboardForSegment(segmentId: Long): Flow<List<SegmentEffortEntity>>

    @Query("SELECT * FROM segment_efforts WHERE segmentId = :segmentId ORDER BY elapsedSeconds ASC LIMIT 1")
    suspend fun getBestEffortForSegment(segmentId: Long): SegmentEffortEntity?

    @Query("SELECT * FROM segment_efforts WHERE sessionId = :sessionId")
    suspend fun getEffortsForSession(sessionId: Long): List<SegmentEffortEntity>

    @Query("SELECT COUNT(*) FROM segment_efforts WHERE segmentId = :segmentId")
    suspend fun getEffortCountForSegment(segmentId: Long): Int
}
