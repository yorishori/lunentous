package com.lunentous.app.data.local

import androidx.room.TypeConverter
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxStatus
import com.lunentous.app.data.local.entity.ReminderStateSource

class Converters {
    @TypeConverter
    fun fromReminderStateSource(value: ReminderStateSource): String = value.name

    @TypeConverter
    fun toReminderStateSource(value: String): ReminderStateSource = ReminderStateSource.valueOf(value)

    @TypeConverter
    fun fromOutboxEntityType(value: OutboxEntityType): String = value.name

    @TypeConverter
    fun toOutboxEntityType(value: String): OutboxEntityType = OutboxEntityType.valueOf(value)

    @TypeConverter
    fun fromOutboxOpType(value: OutboxOpType): String = value.name

    @TypeConverter
    fun toOutboxOpType(value: String): OutboxOpType = OutboxOpType.valueOf(value)

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus): String = value.name

    @TypeConverter
    fun toOutboxStatus(value: String): OutboxStatus = OutboxStatus.valueOf(value)
}
