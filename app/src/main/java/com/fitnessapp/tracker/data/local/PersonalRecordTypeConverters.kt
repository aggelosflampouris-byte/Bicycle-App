package com.fitnessapp.tracker.data.local

import androidx.room.TypeConverter
import com.fitnessapp.tracker.data.local.entity.PersonalRecordType

/**
 * Room TypeConverter for [PersonalRecordType] enum.
 */
class PersonalRecordTypeConverters {

    @TypeConverter
    fun fromPersonalRecordType(value: PersonalRecordType): String = value.name

    @TypeConverter
    fun toPersonalRecordType(value: String): PersonalRecordType =
        PersonalRecordType.entries.firstOrNull { it.name == value } ?: PersonalRecordType.FASTEST_1KM
}
