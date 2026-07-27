package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePlantRequest
import com.lunentous.app.data.remote.dto.PlantDto
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import kotlinx.coroutines.flow.Flow

private data class PlantPayload(
    val name: String,
    val species: String?,
    val location: String?,
    val acquiredDate: String?,
    val generalNotes: String?,
)

/**
 * Reads always come from Room (instant, offline-safe). Writes go local
 * first, then enqueue an outbox op and return immediately -- never
 * blocking the UI on network, whether connected or not (see the Android
 * plan's Build ordering: this replaces phase 2's network-passthrough).
 * Also implements OutboxHandler so OutboxProcessor can dispatch PLANT ops
 * back here, since this is the only place that knows the Plant payload
 * shape and how to reconcile a PlantDto into Room.
 */
class PlantRepository(
    private val dao: PlantDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
) : OutboxHandler {
    override val entityType = OutboxEntityType.PLANT

    fun observeByArchived(archived: Boolean): Flow<List<PlantEntity>> = dao.observeByArchived(archived)

    /** For joins that need every plant's name regardless of archived state
     * (e.g. the dashboard's task list), not just the grid's active plants. */
    fun observeAll(): Flow<List<PlantEntity>> = dao.observeAll()

    fun observeByLocalId(localId: Long): Flow<PlantEntity?> = dao.observeByLocalId(localId)

    suspend fun createPlant(
        name: String,
        species: String?,
        location: String?,
        acquiredDate: String?,
        generalNotes: String?,
    ): Result<PlantEntity> = runCatching {
        val entity = PlantEntity(name = name, species = species, location = location, acquiredDate = acquiredDate, generalNotes = generalNotes, pendingSync = true)
        val localId = dao.upsert(entity)
        outboxRepository.enqueueCreate(entityType, localId, PlantPayload(name, species, location, acquiredDate, generalNotes))
        entity.copy(localId = localId)
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
        val updated = existing.copy(
            name = name,
            species = species,
            location = location,
            acquiredDate = acquiredDate,
            generalNotes = generalNotes,
            dirty = existing.serverId != null,
            pendingSync = true,
        )
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, PlantPayload(name, species, location, acquiredDate, generalNotes))
        updated
    }

    suspend fun setArchived(localId: Long, archived: Boolean): Result<PlantEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Plant $localId not found locally")
        val updated = existing.copy(archived = archived, dirty = existing.serverId != null, pendingSync = true)
        dao.upsert(updated)
        outboxRepository.enqueueArchive(entityType, localId, archived)
        updated
    }

    /** Avatar photos are still network-passthrough -- offline capture
     * doesn't exist yet (that's the phase-6 camera work), so there's
     * nothing to queue when disconnected. */
    suspend fun uploadAvatar(localId: Long, file: okhttp3.MultipartBody.Part): Result<PlantEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Plant $localId not found locally")
        val serverId = existing.serverId ?: error("Cannot upload an avatar before this plant has synced")
        val dto = api.uploadAvatar(serverId, file)
        upsertFromDto(dto, preserveLocalId = existing.localId)
    }

    /** Full-list refetch + upsert-by-serverId + prune, per the plan's Pull
     * sync design. Skips rows with unpushed local edits (dirty=true) so a
     * pull racing ahead of that entity's outbox op can't clobber it with
     * stale pre-edit server data -- the outbox will reconcile it properly
     * once its op succeeds. */
    suspend fun pullSync() {
        if (!sessionStore.hasSession()) return
        val remote = api.getPlants()
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto ->
            if (dao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto)
        }
        dao.getSyncedServerIds().filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, PlantPayload::class.java)
                val dto = api.createPlant(CreatePlantRequest(payload.name, payload.species, payload.location, payload.acquiredDate, payload.generalNotes))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, PlantPayload::class.java)
                val dto = api.updatePlant(serverId, CreatePlantRequest(payload.name, payload.species, payload.location, payload.acquiredDate, payload.generalNotes))
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.ARCHIVE, OutboxOpType.UNARCHIVE -> {
                val serverId = dao.getByLocalId(op.entityLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = if (op.opType == OutboxOpType.ARCHIVE) api.archivePlant(serverId) else api.unarchivePlant(serverId)
                upsertFromDto(dto, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> error("Plants have no DELETE op -- archive-only")
        }
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
