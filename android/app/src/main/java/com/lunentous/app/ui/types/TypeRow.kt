package com.lunentous.app.ui.types

/** Shared row shape for both the Reminder Types and Phase Types screens --
 * `icon` is always null for phase types (hasIcon=false), mirroring
 * TypeManager.tsx's parameterization. `usageCount` is computed locally
 * from Room (see ReminderRuleRepository/PhaseWindowRepository's
 * observeUsageCounts), not the server's join-based usage_count. */
data class TypeRow(
    val localId: Long,
    val name: String,
    val icon: String?,
    val color: String?,
    val archived: Boolean,
    val usageCount: Int,
)
