package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** SERVER = the server's own recomputed value, always authoritative.
 * LOCAL_PROVISIONAL = computed on-device immediately after a local write
 * that would trigger server recompute, purely for instant feedback, and
 * unconditionally overwritten by the next SERVER row that arrives. See
 * the Android plan's "Provisional due-dates" section. */
enum class ReminderStateSource { SERVER, LOCAL_PROVISIONAL }

@Entity(
    tableName = "reminder_states",
    indices = [Index(value = ["plantLocalId", "reminderTypeLocalId"], unique = true)],
)
data class ReminderStateEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val reminderTypeLocalId: Long,
    val dueDate: String? = null,
    val notified: Boolean = false,
    val source: ReminderStateSource = ReminderStateSource.SERVER,
    val computedAt: Long = 0,
)
