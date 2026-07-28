package com.lunentous.app.data.sync.outbox

import android.content.Context
import com.google.gson.Gson
import com.lunentous.app.data.local.dao.OutboxDao
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.OutboxStatus
import com.lunentous.app.ui.widget.refreshLunentousWidget
import kotlinx.coroutines.flow.Flow

/**
 * Enqueues writes for OutboxProcessor to work through in order. Implements
 * exactly the two squash rules from the Android plan's "Outbox -- single
 * serial FIFO queue": a new UPDATE merges into an existing pending
 * CREATE/UPDATE for the same entity instead of adding a row, and a DELETE
 * on an entity whose only pending op is a CREATE drops both with no
 * network call ever happening. Every write-capable repository calls this
 * instead of touching OutboxDao directly.
 *
 * Every successful enqueue schedules a sync attempt (see SyncScheduler) --
 * the repositories that call this never have to remember to do so
 * themselves. It also refreshes the home screen widget right here rather
 * than only after OutboxSyncWorker eventually runs -- that worker requires
 * network connectivity, but the local Room write behind every one of these
 * enqueue calls has already landed by this point regardless of network
 * state, so the widget should reflect it immediately rather than only once
 * a connection comes back.
 */
class OutboxRepository(private val dao: OutboxDao, private val gson: Gson, private val appContext: Context) {
    fun observePending(): Flow<List<OutboxOperationEntity>> = dao.observePending()
    fun observeFailed(): Flow<List<OutboxOperationEntity>> = dao.observeFailed()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    fun observeFailedCount(): Flow<Int> = dao.observeFailedCount()

    suspend fun nextPending(): OutboxOperationEntity? = dao.nextPending()

    suspend fun enqueueCreate(entityType: OutboxEntityType, entityLocalId: Long, payload: Any) {
        dao.insert(
            OutboxOperationEntity(
                entityType = entityType,
                entityLocalId = entityLocalId,
                opType = OutboxOpType.CREATE,
                payloadJson = gson.toJson(payload),
            ),
        )
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
    }

    /** Squashes into a pending CREATE or UPDATE for the same entity if one
     * exists, replacing its payload outright -- CREATE and UPDATE payloads
     * carry the same fields for every entity type, so the merged CREATE
     * still has everything it needs when it eventually runs. */
    suspend fun enqueueUpdate(entityType: OutboxEntityType, entityLocalId: Long, payload: Any) {
        val existing = dao.findPendingForEntity(entityType, entityLocalId, listOf(OutboxOpType.CREATE, OutboxOpType.UPDATE))
        if (existing != null) {
            dao.updatePayload(existing.id, gson.toJson(payload))
        } else {
            dao.insert(
                OutboxOperationEntity(
                    entityType = entityType,
                    entityLocalId = entityLocalId,
                    opType = OutboxOpType.UPDATE,
                    payloadJson = gson.toJson(payload),
                ),
            )
        }
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
    }

    /** Returns true if the entity never made it to the server (only had a
     * pending CREATE) -- the caller should hard-delete the local row
     * itself in that case, since there's nothing left to sync. Otherwise
     * enqueues a real DELETE op, first dropping any now-moot pending
     * UPDATE for the same entity. */
    suspend fun enqueueDelete(entityType: OutboxEntityType, entityLocalId: Long): Boolean {
        val pendingCreate = dao.findPendingForEntity(entityType, entityLocalId, listOf(OutboxOpType.CREATE))
        if (pendingCreate != null && pendingCreate.opType == OutboxOpType.CREATE) {
            dao.deleteById(pendingCreate.id)
            refreshLunentousWidget(appContext)
            return true
        }
        dao.findPendingUpdateForEntity(entityType, entityLocalId)?.let { dao.deleteById(it.id) }
        dao.insert(
            OutboxOperationEntity(
                entityType = entityType,
                entityLocalId = entityLocalId,
                opType = OutboxOpType.DELETE,
                payloadJson = "{}",
            ),
        )
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
        return false
    }

    suspend fun enqueueArchive(entityType: OutboxEntityType, entityLocalId: Long, archived: Boolean) {
        dao.insert(
            OutboxOperationEntity(
                entityType = entityType,
                entityLocalId = entityLocalId,
                opType = if (archived) OutboxOpType.ARCHIVE else OutboxOpType.UNARCHIVE,
                payloadJson = "{}",
            ),
        )
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
    }

    /** No squashing -- each call just adds its own op, so photos captured
     * in separate sessions each get their own (small) upload request
     * instead of needing to track and merge a growing pending file list. */
    suspend fun enqueueAppendPhotos(entityType: OutboxEntityType, entityLocalId: Long, payload: Any) {
        dao.insert(
            OutboxOperationEntity(
                entityType = entityType,
                entityLocalId = entityLocalId,
                opType = OutboxOpType.APPEND_PHOTOS,
                payloadJson = gson.toJson(payload),
            ),
        )
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
    }

    suspend fun markInFlight(id: Long) = dao.setStatus(id, OutboxStatus.IN_FLIGHT)
    suspend fun markPending(id: Long) = dao.setStatus(id, OutboxStatus.PENDING)
    suspend fun markFailed(id: Long, error: String?) = dao.markFailed(id, error)
    suspend fun remove(id: Long) = dao.deleteById(id)

    suspend fun retry(id: Long) {
        dao.markPendingRetry(id)
        SyncScheduler.triggerOutboxSync(appContext)
        refreshLunentousWidget(appContext)
    }
    suspend fun discard(id: Long) = dao.deleteById(id)
}
