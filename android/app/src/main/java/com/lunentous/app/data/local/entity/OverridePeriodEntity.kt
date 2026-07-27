package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Not independently synced -- always travels embedded in its parent
 * rule's create/update payload, matching the server's PATCH
 * /reminder-rules/:id delete-all-reinsert semantics. No dirty/pendingSync
 * of its own; editing one just marks the parent rule dirty. */
@Entity(tableName = "override_periods", indices = [Index(value = ["reminderRuleLocalId"])])
data class OverridePeriodEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val reminderRuleLocalId: Long,
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
    val intervalDays: Int? = null,
)
