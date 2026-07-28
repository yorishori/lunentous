package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-plant, untyped, informational reminder -- no reminder type, and
 * completing one never writes a TimelineEventEntity (unlike a normal
 * reminder occurrence). completedAt is kept (not cleared to deleted) once
 * set, so there's a record of what was done. Mirrors one_time_reminders. */
@Entity(tableName = "one_time_reminders", indices = [Index(value = ["serverId"], unique = true)])
data class OneTimeReminderEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val dueDate: String,
    val text: String,
    val completedAt: String? = null,
    val createdAt: String = "",
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
