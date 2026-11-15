package com.fitnessapp.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TwinSnapshotDao {
    @Query("SELECT * FROM twin_snapshots ORDER BY dateKey DESC LIMIT 1")
    fun getLatestSnapshotFlow(): Flow<TwinSnapshotEntity?>

    @Query("SELECT * FROM twin_snapshots ORDER BY dateKey DESC LIMIT 1")
    suspend fun getLatestSnapshot(): TwinSnapshotEntity?

    @Query("SELECT * FROM twin_snapshots ORDER BY dateKey DESC LIMIT :limit")
    fun getRecentSnapshotsFlow(limit: Int = 30): Flow<List<TwinSnapshotEntity>>

    @Query("SELECT * FROM twin_snapshots ORDER BY dateKey DESC")
    suspend fun getAllSnapshots(): List<TwinSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: TwinSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<TwinSnapshotEntity>)
}

