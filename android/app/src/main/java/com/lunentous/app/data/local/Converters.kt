package com.lunentous.app.data.local

import androidx.room.TypeConverter
import com.lunentous.app.data.local.entity.ReminderStateSource

class Converters {
    @TypeConverter
    fun fromReminderStateSource(value: ReminderStateSource): String = value.name

    @TypeConverter
    fun toReminderStateSource(value: String): ReminderStateSource = ReminderStateSource.valueOf(value)
}
