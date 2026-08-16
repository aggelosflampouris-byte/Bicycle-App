package com.fitnessapp.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity
import com.fitnessapp.tracker.data.local.entity.PersonalRecordType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {

    @Query("SELECT * FROM personal_records WHERE activityType = :activityType ORDER BY recordType ASC")
    fun getRecordsForActivityFlow(activityType: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE activityType = :activityType ORDER BY recordType ASC")
    suspend fun getRecordsForActivity(activityType: String): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records WHERE activityType = :activityType AND recordType = :recordType LIMIT 1")
    suspend fun getRecord(activityType: String, recordType: PersonalRecordType): PersonalRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: PersonalRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecords(records: List<PersonalRecordEntity>)

    @Query("DELETE FROM personal_records WHERE activityType = :activityType")
    suspend fun clearRecordsForActivity(activityType: String)

    @Query("DELETE FROM personal_records")
    suspend fun deleteAll()
}
