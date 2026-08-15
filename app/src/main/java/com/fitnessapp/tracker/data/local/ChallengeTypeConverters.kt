package com.fitnessapp.tracker.data.local

import androidx.room.TypeConverter
import com.fitnessapp.tracker.data.local.entity.ChallengeMetric
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.data.local.entity.ChallengePeriod

/**
 * Room TypeConverters for the [ChallengeEntity] enum fields.
 * Stores enum values as their [name] string in SQLite, so existing data
 * is fully backward-compatible (no column-level migration required).
 */
class ChallengeTypeConverters {

    @TypeConverter
    fun fromChallengeStatus(value: ChallengeStatus): String = value.name

    @TypeConverter
    fun toChallengeStatus(value: String): ChallengeStatus =
        ChallengeStatus.entries.firstOrNull { it.name == value } ?: ChallengeStatus.PENDING

    @TypeConverter
    fun fromChallengeMetric(value: ChallengeMetric): String = value.name

    @TypeConverter
    fun toChallengeMetric(value: String): ChallengeMetric =
        ChallengeMetric.entries.firstOrNull { it.name == value } ?: ChallengeMetric.DISTANCE

    @TypeConverter
    fun fromChallengePeriod(value: ChallengePeriod): String = value.name

    @TypeConverter
    fun toChallengePeriod(value: String): ChallengePeriod =
        ChallengePeriod.entries.firstOrNull { it.name == value } ?: ChallengePeriod.WEEK
}
