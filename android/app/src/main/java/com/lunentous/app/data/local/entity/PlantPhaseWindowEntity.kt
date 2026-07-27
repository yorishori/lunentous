package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Purely informational, never drives reminders -- mirrors
 * plant_phase_windows. */
@Entity(tableName = "plant_phase_windows", indices = [Index(value = ["serverId"], unique = true)])
data class PlantPhaseWindowEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val phaseTypeLocalId: Long,
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
    val notes: String? = null,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
