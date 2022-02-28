package com.jack.qrcodefor1922.ui.database

import androidx.room.TypeConverter
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestame(value: Long) : Date {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date): Long {
        return date.time.toLong()
    }
}