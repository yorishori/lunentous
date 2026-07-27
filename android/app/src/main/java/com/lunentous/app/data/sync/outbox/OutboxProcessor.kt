package com.lunentous.app.data.sync.outbox

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.entity.OutboxEntityType
import java.io.IOException
import retrofit2.HttpException

/**
 * Works through OutboxRepository's queue strictly in FIFO order, one op at
 * a time, dispatching to the handler registered for that op's entity type.
 * Stops at the first retryable failure or reauth requirement (leaving it
 * PENDING for WorkManager to retry) rather than skipping ahead, since a
 * transient failure is assumed to affect the whole connection -- see the
 * Android plan's OutboxProcessor.processNext() execution rules.
 */
class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val handlers: Map<OutboxEntityType, OutboxHandler>,
    private val sessionStore: SessionStore,
) {
    /** Returns true if the queue was fully drained, false if it stopped
     * early on a retryable failure or reauth requirement. */
    suspend fun processQueue(): Boolean {
        while (true) {
            val op = outboxRepository.nextPending() ?: return true
            outboxRepository.markInFlight(op.id)

            val handler = handlers.getValue(op.entityType)
            val result = runCatching { handler.process(op) }.getOrElse { classifyException(it) }

            when (result) {
                is OutboxResult.Success -> outboxRepository.remove(op.id)
                is OutboxResult.NonRetryableFailure -> outboxRepository.markFailed(op.id, result.message)
                is OutboxResult.CascadeFailed -> outboxRepository.markFailed(op.id, "A related item failed to sync")
                is OutboxResult.RetryableFailure -> {
                    outboxRepository.markPending(op.id)
                    return false
                }
                is OutboxResult.ReauthRequired -> {
                    outboxRepository.markPending(op.id)
                    sessionStore.markReauthRequired()
                    return false
                }
            }
        }
    }

    private fun classifyException(e: Throwable): OutboxResult = when {
        e is HttpException && e.code() == 401 -> OutboxResult.ReauthRequired
        e is HttpException && e.code() in 400..499 -> OutboxResult.NonRetryableFailure(httpMessage(e))
        e is HttpException -> OutboxResult.RetryableFailure(httpMessage(e))
        e is IOException -> OutboxResult.RetryableFailure(e.message ?: "Network error")
        else -> OutboxResult.NonRetryableFailure(e.message ?: "Unexpected error")
    }

    private fun httpMessage(e: HttpException): String =
        runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.takeIf { it.isNotBlank() } ?: e.message()
}
