package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Same shape as ReminderTypeEntity minus icon -- mirrors phase_types
 * (the server intentionally has no icon column on this table). */
@Entity(tableName = "phase_types", indices = [Index(value = ["serverId"], unique = true)])
data class PhaseTypeEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val name: String,
    val color: String? = null,
    val archived: Boolean = false,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
