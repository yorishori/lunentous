package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Archivable, never hard-deleted server-side -- mirrors reminder_types. */
@Entity(tableName = "reminder_types", indices = [Index(value = ["serverId"], unique = true)])
data class ReminderTypeEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
