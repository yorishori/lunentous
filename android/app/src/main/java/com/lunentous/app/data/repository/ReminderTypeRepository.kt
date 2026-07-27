package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreateReminderTypeRequest
import com.lunentous.app.data.remote.dto.ReminderTypeDto
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import kotlinx.coroutines.flow.Flow

private data class ReminderTypePayload(val name: String, val icon: String?, val color: String?)

class ReminderTypeRepository(
    private val dao: ReminderTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
) : OutboxHandler {
    override val entityType = OutboxEntityType.REMINDER_TYPE

    fun observeByArchived(archived: Boolean): Flow<List<ReminderTypeEntity>> = dao.observeByArchived(archived)

    /** Archived types can still be referenced by existing reminder rules,
     * so lookups that need name/icon/color for display (e.g. the dashboard
     * task list) should join against all types, not just active ones. */
    fun observeAll(): Flow<List<ReminderTypeEntity>> = dao.observeAll()

    suspend fun create(name: String, icon: String?, color: String?): Result<ReminderTypeEntity> = runCatching {
        val entity = ReminderTypeEntity(name = name, icon = icon, color = color, pendingSync = true)
        val localId = dao.upsert(entity)
        outboxRepository.enqueueCreate(entityType, localId, ReminderTypePayload(name, icon, color))
        entity.copy(localId = localId)
    }

    suspend fun update(localId: Long, name: String, icon: String?, color: String?): Result<ReminderTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Reminder type $localId not found locally")
        val updated = existing.copy(name = name, icon = icon, color = color, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, ReminderTypePayload(name, icon, color))
        updated
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<ReminderTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Reminder type $localId not found locally")
        val updated = existing.copy(archived = archived, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueArchive(entityType, localId, archived)
        updated
    }

    /** Skips rows with unpushed local edits -- see PlantRepository.pullSync
     * for why. */
    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getReminderTypes()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto -> if (dao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto) }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, ReminderTypePayload::class.java)
                val dto = api.createReminderType(CreateReminderTypeRequest(payload.name, payload.icon, payload.color))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, ReminderTypePayload::class.java)
                val dto = api.updateReminderType(serverId, CreateReminderTypeRequest(payload.name, payload.icon, payload.color))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.ARCHIVE, OutboxOpType.UNARCHIVE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = if (op.opType == OutboxOpType.ARCHIVE) api.archiveReminderType(serverId) else api.unarchiveReminderType(serverId)
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> error("Reminder types have no DELETE op -- archive-only")
        }
    }

    private suspend fun upsertFromDto(dto: ReminderTypeDto, preserveLocalId: Long? = null): ReminderTypeEntity {
        val existing = preserveLocalId?.let { dao.getByLocalId(it) } ?: dao.getByServerId(dto.id)
        val entity = ReminderTypeEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            name = dto.name,
            icon = dto.icon,
            color = dto.color,
            archived = dto.archived == 1,
        )
        val newLocalId = dao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }
}
