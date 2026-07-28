package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One rule per (plant, reminder type) -- the unique index below mirrors
 * the server's UNIQUE(plant_id, reminder_type_id) and lets offline
 * duplicate-rule creation be caught locally before it'd ever reach the
 * server's own constraint. */
@Entity(
    tableName = "reminder_rules",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["plantLocalId", "reminderTypeLocalId"], unique = true),
    ],
)
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val reminderTypeLocalId: Long,
    val defaultIntervalDays: Int? = null,
    // Mutually exclusive with defaultIntervalDays/override periods -- a
    // fixed calendar date the reminder recurs on every year instead of an
    // N-day interval. Mirrors the server's annual_month/annual_day.
    val annualMonth: Int? = null,
    val annualDay: Int? = null,
    val createdAt: String = "",
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
