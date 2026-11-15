package com.fitnessapp.tracker.data.local

import androidx.room.TypeConverter
import com.fitnessapp.tracker.data.local.entity.CheckInType

class TwinTypeConverters {
    @TypeConverter
    fun fromCheckInType(value: CheckInType): String = value.name

    @TypeConverter
    fun toCheckInType(value: String): CheckInType = runCatching {
        CheckInType.valueOf(value)
    }.getOrDefault(CheckInType.DAILY)
}

