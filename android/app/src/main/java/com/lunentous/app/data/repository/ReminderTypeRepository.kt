package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreateReminderTypeRequest
import com.lunentous.app.data.remote.dto.ReminderTypeDto
import kotlinx.coroutines.flow.Flow

class ReminderTypeRepository(
    private val dao: ReminderTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByArchived(archived: Boolean): Flow<List<ReminderTypeEntity>> = dao.observeByArchived(archived)

    suspend fun create(name: String, icon: String?, color: String?): Result<ReminderTypeEntity> = runCatching {
        if (sessionStore.hasSession()) {
            upsertFromDto(api.createReminderType(CreateReminderTypeRequest(name, icon, color)))
        } else {
            val entity = ReminderTypeEntity(name = name, icon = icon, color = color)
            entity.copy(localId = dao.upsert(entity))
        }
    }

    suspend fun update(localId: Long, name: String, icon: String?, color: String?): Result<ReminderTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Reminder type $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            upsertFromDto(api.updateReminderType(existing.serverId, CreateReminderTypeRequest(name, icon, color)), preserveLocalId = localId)
        } else {
            val updated = existing.copy(name = name, icon = icon, color = color, dirty = existing.serverId != null)
            dao.upsert(updated)
            updated
        }
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<ReminderTypeEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Reminder type $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = if (archived) api.archiveReminderType(existing.serverId) else api.unarchiveReminderType(existing.serverId)
            upsertFromDto(dto, preserveLocalId = localId)
        } else {
            val updated = existing.copy(archived = archived, dirty = existing.serverId != null)
            dao.upsert(updated)
            updated
        }
    }

    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getReminderTypes()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { upsertFromDto(it) }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
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
