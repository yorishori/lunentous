package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhaseTypeDao
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePhaseTypeRequest
import com.lunentous.app.data.remote.dto.PhaseTypeDto
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import kotlinx.coroutines.flow.Flow

private data class PhaseTypePayload(val name: String, val color: String?)

class PhaseTypeRepository(
    private val dao: PhaseTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
) : OutboxHandler {
    override val entityType = OutboxEntityType.PHASE_TYPE

    fun observeByArchived(archived: Boolean): Flow<List<PhaseTypeEntity>> = dao.observeByArchived(archived)

    suspend fun create(name: String, color: String?): Result<PhaseTypeEntity> = runCatching {
        val entity = PhaseTypeEntity(name = name, color = color, pendingSync = true)
        val localId = dao.upsert(entity)
        outboxRepository.enqueueCreate(entityType, localId, PhaseTypePayload(name, color))
        entity.copy(localId = localId)
    }

    suspend fun update(localId: Long, name: String, color: String?): Result<PhaseTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Phase type $localId not found locally")
        val updated = existing.copy(name = name, color = color, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, PhaseTypePayload(name, color))
        updated
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<PhaseTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Phase type $localId not found locally")
        val updated = existing.copy(archived = archived, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueArchive(entityType, localId, archived)
        updated
    }

    /** Skips rows with unpushed local edits -- see PlantRepository.pullSync
     * for why. */
    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getPhaseTypes()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto -> if (dao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto) }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, PhaseTypePayload::class.java)
                val dto = api.createPhaseType(CreatePhaseTypeRequest(payload.name, payload.color))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, PhaseTypePayload::class.java)
                val dto = api.updatePhaseType(serverId, CreatePhaseTypeRequest(payload.name, payload.color))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.ARCHIVE, OutboxOpType.UNARCHIVE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = if (op.opType == OutboxOpType.ARCHIVE) api.archivePhaseType(serverId) else api.unarchivePhaseType(serverId)
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> error("Phase types have no DELETE op -- archive-only")
            OutboxOpType.APPEND_PHOTOS -> error("Phase types don't support APPEND_PHOTOS")
        }
    }

    private suspend fun upsertFromDto(dto: PhaseTypeDto, preserveLocalId: Long? = null): PhaseTypeEntity {
        val existing = preserveLocalId?.let { dao.getByLocalId(it) } ?: dao.getByServerId(dto.id)
        val entity = PhaseTypeEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            name = dto.name,
            color = dto.color,
            archived = dto.archived == 1,
        )
        val newLocalId = dao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }
}
