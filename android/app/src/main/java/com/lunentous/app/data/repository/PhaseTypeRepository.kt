package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhaseTypeDao
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePhaseTypeRequest
import com.lunentous.app.data.remote.dto.PhaseTypeDto
import kotlinx.coroutines.flow.Flow

class PhaseTypeRepository(
    private val dao: PhaseTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByArchived(archived: Boolean): Flow<List<PhaseTypeEntity>> = dao.observeByArchived(archived)

    suspend fun create(name: String, color: String?): Result<PhaseTypeEntity> = runCatching {
        if (sessionStore.hasSession()) {
            upsertFromDto(api.createPhaseType(CreatePhaseTypeRequest(name, color)))
        } else {
            val entity = PhaseTypeEntity(name = name, color = color)
            entity.copy(localId = dao.upsert(entity))
        }
    }

    suspend fun update(localId: Long, name: String, color: String?): Result<PhaseTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Phase type $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            upsertFromDto(api.updatePhaseType(existing.serverId, CreatePhaseTypeRequest(name, color)), preserveLocalId = localId)
        } else {
            val updated = existing.copy(name = name, color = color, dirty = existing.serverId != null)
            dao.upsert(updated)
            updated
        }
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<PhaseTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Phase type $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = if (archived) api.archivePhaseType(existing.serverId) else api.unarchivePhaseType(existing.serverId)
            upsertFromDto(dto, preserveLocalId = localId)
        } else {
            val updated = existing.copy(archived = archived, dirty = existing.serverId != null)
            dao.upsert(updated)
            updated
        }
    }

    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getPhaseTypes()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { upsertFromDto(it) }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
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
