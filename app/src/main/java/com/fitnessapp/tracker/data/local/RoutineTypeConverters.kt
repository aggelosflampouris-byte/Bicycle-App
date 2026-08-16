package com.fitnessapp.tracker.data.local

import androidx.room.TypeConverter
import com.fitnessapp.tracker.data.local.entity.RoutineInterval
import com.fitnessapp.tracker.data.local.entity.RoutineMetric

/**
 * Room TypeConverters for the [RoutineEntity] enum fields.
 * Stores enum values as their [name] string in SQLite, so existing data
 * is fully backward-compatible.
 */
class RoutineTypeConverters {

    @TypeConverter
    fun fromRoutineInterval(value: RoutineInterval): String = value.name

    @TypeConverter
    fun toRoutineInterval(value: String): RoutineInterval =
        RoutineInterval.entries.firstOrNull { it.name == value } ?: RoutineInterval.WEEKLY

    @TypeConverter
    fun fromRoutineMetric(value: RoutineMetric): String = value.name

    @TypeConverter
    fun toRoutineMetric(value: String): RoutineMetric =
        RoutineMetric.entries.firstOrNull { it.name == value } ?: RoutineMetric.DISTANCE
}
