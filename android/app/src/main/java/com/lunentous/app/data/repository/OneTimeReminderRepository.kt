package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.OneTimeReminderDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.entity.OneTimeReminderEntity
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreateOneTimeReminderRequest
import com.lunentous.app.data.remote.dto.OneTimeReminderDto
import com.lunentous.app.data.remote.dto.UpdateOneTimeReminderRequest
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

private data class ReminderPayload(val dueDate: String, val text: String, val completedAt: String?)

/**
 * Per-plant, untyped, informational reminders -- no reminder type, and
 * completing one never touches TimelineEventEntity/ReminderStateEntity.
 * Same offline-first shape as PhaseWindowRepository: write-local-then-
 * enqueue, with a distinct setCompleted() alongside create/update/delete
 * since completing one is kept (an UPDATE), not deleted.
 */
class OneTimeReminderRepository(
    private val dao: OneTimeReminderDao,
    private val plantDao: PlantDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
) : OutboxHandler {
    override val entityType = OutboxEntityType.ONE_TIME_REMINDER

    fun observeByPlant(plantLocalId: Long): Flow<List<OneTimeReminderEntity>> = dao.observeByPlant(plantLocalId)

    /** Across every plant -- used by the Dashboard and Care Timeline. */
    fun observeAll(): Flow<List<OneTimeReminderEntity>> = dao.observeAll()

    suspend fun create(plantLocalId: Long, dueDate: String, text: String): Result<OneTimeReminderEntity> = runCatching {
        val entity = OneTimeReminderEntity(
            plantLocalId = plantLocalId,
            dueDate = dueDate,
            text = text,
            createdAt = LocalDate.now().toString(),
            pendingSync = true,
        )
        val localId = dao.upsert(entity)
        outboxRepository.enqueueCreate(entityType, localId, ReminderPayload(dueDate, text, null))
        entity.copy(localId = localId)
    }

    suspend fun update(localId: Long, dueDate: String, text: String): Result<OneTimeReminderEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("One-time reminder $localId not found locally")
        val updated = existing.copy(dueDate = dueDate, text = text, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, ReminderPayload(dueDate, text, existing.completedAt))
        updated
    }

    /** Marks complete (kept, not deleted) or reverses that -- the "Complete"
     * / "Undo" action on the Dashboard, Care Timeline, and Plant Detail. */
    suspend fun setCompleted(localId: Long, completed: Boolean): Result<OneTimeReminderEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("One-time reminder $localId not found locally")
        val completedAt = if (completed) Instant.now().toString() else null
        val updated = existing.copy(completedAt = completedAt, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, ReminderPayload(existing.dueDate, existing.text, completedAt))
        updated
    }

    suspend fun delete(localId: Long): Result<Unit> = runCatching {
        val existing = dao.getByLocalId(localId) ?: return@runCatching
        val localOnly = outboxRepository.enqueueDelete(entityType, localId)
        if (localOnly) {
            dao.deleteByLocalId(localId)
        } else {
            // Tombstone rather than hard-delete -- OutboxProcessor still
            // needs this row's serverId when the DELETE op actually runs.
            dao.upsert(existing.copy(deleted = true, pendingSync = true))
        }
    }

    /** Skips rows with unpushed local edits -- see PlantRepository.pullSync
     * for why. */
    suspend fun pullSyncForPlant(plantLocalId: Long) {
        if (!sessionStore.hasSession()) return
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId ?: return

        val remote = api.getOneTimeReminders(plantServerId)
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto ->
            if (dao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto, plantLocalId)
        }
        dao.getSyncedServerIdsForPlant(plantLocalId).filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        val reminder = dao.getByLocalId(op.entityLocalId) ?: return OutboxResult.Success // already gone locally, nothing to do
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, ReminderPayload::class.java)
                val plantServerId = plantDao.getByLocalId(reminder.plantLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = api.createOneTimeReminder(plantServerId, CreateOneTimeReminderRequest(payload.dueDate, payload.text))
                upsertFromDto(dto, reminder.plantLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = reminder.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, ReminderPayload::class.java)
                val dto = api.updateOneTimeReminder(serverId, UpdateOneTimeReminderRequest(payload.dueDate, payload.text, payload.completedAt))
                upsertFromDto(dto, reminder.plantLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> {
                reminder.serverId?.let { api.deleteOneTimeReminder(it) }
                dao.deleteByLocalId(op.entityLocalId)
                OutboxResult.Success
            }
            else -> error("One-time reminders only support CREATE/UPDATE/DELETE")
        }
    }

    private suspend fun upsertFromDto(dto: OneTimeReminderDto, plantLocalId: Long, preserveLocalId: Long? = null): OneTimeReminderEntity {
        val existing = preserveLocalId?.let { dao.getByLocalId(it) } ?: dao.getByServerId(dto.id)
        val entity = OneTimeReminderEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            plantLocalId = plantLocalId,
            dueDate = dto.dueDate,
            text = dto.text,
            completedAt = dto.completedAt,
            createdAt = dto.createdAt,
        )
        val newLocalId = dao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }
}
