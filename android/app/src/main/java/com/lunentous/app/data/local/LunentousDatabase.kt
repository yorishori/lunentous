package com.lunentous.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lunentous.app.data.local.dao.OneTimeReminderDao
import com.lunentous.app.data.local.dao.OutboxDao
import com.lunentous.app.data.local.dao.OverridePeriodDao
import com.lunentous.app.data.local.dao.PhaseTypeDao
import com.lunentous.app.data.local.dao.PhaseWindowDao
import com.lunentous.app.data.local.dao.PhotoDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderRuleDao
import com.lunentous.app.data.local.dao.ReminderStateDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.dao.TimelineEventDao
import com.lunentous.app.data.local.entity.OneTimeReminderEntity
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PhotoEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.local.entity.ReminderRuleEntity
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.local.entity.TimelineEventEntity

@Database(
    entities = [
        PlantEntity::class,
        ReminderTypeEntity::class,
        PhaseTypeEntity::class,
        ReminderRuleEntity::class,
        OverridePeriodEntity::class,
        ReminderStateEntity::class,
        PlantPhaseWindowEntity::class,
        TimelineEventEntity::class,
        PhotoEntity::class,
        OutboxOperationEntity::class,
        OneTimeReminderEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LunentousDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun reminderTypeDao(): ReminderTypeDao
    abstract fun phaseTypeDao(): PhaseTypeDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun overridePeriodDao(): OverridePeriodDao
    abstract fun reminderStateDao(): ReminderStateDao
    abstract fun phaseWindowDao(): PhaseWindowDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun photoDao(): PhotoDao
    abstract fun outboxDao(): OutboxDao
    abstract fun oneTimeReminderDao(): OneTimeReminderDao
}
