package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The unified log -- a non-null reminderTypeLocalId means "this logs
 * completing that reminder" (triggers recompute server-side); null means
 * a plain journal note. Mirrors timeline_events. */
@Entity(
    tableName = "timeline_events",
    indices = [Index(value = ["serverId"], unique = true), Index(value = ["plantLocalId", "eventDate"])],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val reminderTypeLocalId: Long? = null,
    val eventDate: String,
    val text: String? = null,
    val createdAt: String = "",
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
