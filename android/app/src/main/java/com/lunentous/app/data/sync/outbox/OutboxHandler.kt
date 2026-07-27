package com.lunentous.app.data.sync.outbox

import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOperationEntity

/** What happened when a single outbox op was processed -- see
 * OutboxProcessor for how each variant is handled. */
sealed interface OutboxResult {
    /** Network call succeeded and Room was reconciled with the server's
     * response; the op can be removed from the queue. */
    data object Success : OutboxResult

    /** A 4xx the server will never accept differently (validation, 404,
     * 409 duplicate) -- mark FAILED and move on to the next op, since one
     * bad op shouldn't block everything behind it. */
    data class NonRetryableFailure(val message: String) : OutboxResult

    /** Timeout/5xx/connection drop -- assumed to affect the whole
     * connection, not just this op, so leave it PENDING and stop the
     * whole queue for WorkManager to retry with backoff. */
    data class RetryableFailure(val message: String) : OutboxResult

    /** This op's required parent (e.g. a reminder rule's reminder type)
     * never got a serverId because its own CREATE permanently failed --
     * cascade-fail without ever making a network call. */
    data object CascadeFailed : OutboxResult

    /** 401 -- don't touch backoff, just flag that the API key needs to be
     * re-entered (SessionStore.markReauthRequired) and stop the queue. */
    data object ReauthRequired : OutboxResult
}

/** Each write-capable repository implements this for exactly its own
 * OutboxEntityType, keeping op-specific logic (which payload shape, which
 * endpoint, how to reconcile the response into Room) colocated with the
 * rest of that entity's repository rather than centralized in one giant
 * processor. */
interface OutboxHandler {
    val entityType: OutboxEntityType
    suspend fun process(op: OutboxOperationEntity): OutboxResult
}
