package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePlantRequest
import com.lunentous.app.data.remote.dto.PlantDto
import kotlinx.coroutines.flow.Flow

/**
 * Reads always come from Room (instant, offline-safe). Writes go straight
 * through to the network when a server is connected (this is phase 2 --
 * "network-passthrough"; the real write-local-then-outbox pattern lands in
 * a later phase, see the Android plan's Build ordering) or straight to
 * Room, marked local-only, when it isn't.
 */
class PlantRepository(
    private val dao: PlantDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByArchived(archived: Boolean): Flow<List<PlantEntity>> = dao.observeByArchived(archived)

    fun observeByLocalId(localId: Long): Flow<PlantEntity?> = dao.observeByLocalId(localId)

    suspend fun createPlant(
        name: String,
        species: String?,
        location: String?,
        acquiredDate: String?,
        generalNotes: String?,
    ): Result<PlantEntity> = runCatching {
        if (sessionStore.hasSession()) {
            val dto = api.createPlant(CreatePlantRequest(name, species, location, acquiredDate, generalNotes))
            upsertFromDto(dto)
        } else {
            val entity = PlantEntity(name = name, species = species, location = location, acquiredDate = acquiredDate, generalNotes = generalNotes)
            entity.copy(localId = dao.upsert(entity))
        }
    }

    suspend fun updatePlant(
        localId: Long,
        name: String,
        species: String?,
        location: String?,
        acquiredDate: String?,
        generalNotes: String?,
    ): Result<PlantEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Plant $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = api.updatePlant(existing.serverId, CreatePlantRequest(name, species, location, acquiredDate, generalNotes))
            upsertFromDto(dto, preserveLocalId = existing.localId)
        } else {
            val updated = existing.copy(
                name = name,
                species = species,
                location = location,
                acquiredDate = acquiredDate,
                generalNotes = generalNotes,
                dirty = existing.serverId != null,
            )
            dao.upsert(updated)
            updated
        }
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<PlantEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Plant $localId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = if (archived) api.archivePlant(existing.serverId) else api.unarchivePlant(existing.serverId)
            upsertFromDto(dto, preserveLocalId = existing.localId)
        } else {
            val updated = existing.copy(archived = archived, dirty = existing.serverId != null)
            dao.upsert(updated)
            updated
        }
    }

    suspend fun uploadAvatar(localId: Long, file: okhttp3.MultipartBody.Part): Result<PlantEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Plant $localId not found locally")
        val serverId = existing.serverId ?: error("Cannot upload an avatar before this plant has synced")
        val dto = api.uploadAvatar(serverId, file)
        upsertFromDto(dto, preserveLocalId = existing.localId)
    }

    /** Full-list refetch + upsert-by-serverId + prune, per the plan's Pull
     * sync design -- personal-scale data makes this simpler and cheaper
     * than a delta protocol the server doesn't expose anyway. */
    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getPlants()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { upsertFromDto(it) }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    private suspend fun upsertFromDto(dto: PlantDto, preserveLocalId: Long? = null): PlantEntity {
        val existing = preserveLocalId?.let { dao.getByLocalId(it) } ?: dao.getByServerId(dto.id)
        val entity = PlantEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            name = dto.name,
            species = dto.species,
            location = dto.location,
            acquiredDate = dto.acquiredDate,
            avatarPhotoLocalId = existing?.avatarPhotoLocalId,
            avatarPhotoPath = dto.avatarPhotoPath,
            generalNotes = dto.generalNotes,
            archived = dto.archived == 1,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
        val newLocalId = dao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }
}
