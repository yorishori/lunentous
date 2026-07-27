package com.lunentous.app.data.local.dao

/** Local usage-count projection, computed from what's actually in Room --
 * unlike the server's usage_count (a join count only present in its list
 * response), this is derived offline so it stays correct without a
 * connection. Shared shape for both reminder-rule and phase-window counts. */
data class TypeUsageCount(val typeLocalId: Long, val count: Int)
