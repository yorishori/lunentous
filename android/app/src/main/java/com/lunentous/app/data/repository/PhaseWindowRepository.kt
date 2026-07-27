package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhaseTypeDao
import com.lunentous.app.data.local.dao.PhaseWindowDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.TypeUsageCount
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePhaseWindowRequest
import com.lunentous.app.data.remote.dto.PhaseWindowDto
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import kotlinx.coroutines.flow.Flow

private data class WindowPayload(
    val phaseTypeLocalId: Long,
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
    val notes: String?,
)

class PhaseWindowRepository(
    private val dao: PhaseWindowDao,
    private val plantDao: PlantDao,
    private val phaseTypeDao: PhaseTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
) : OutboxHandler {
    override val entityType = OutboxEntityType.PHASE_WINDOW

    fun observeByPlant(plantLocalId: Long): Flow<List<PlantPhaseWindowEntity>> = dao.observeByPlant(plantLocalId)

    /** Across every plant -- used by the Calendar screen. */
    fun observeAll(): Flow<List<PlantPhaseWindowEntity>> = dao.observeAll()

    /** Computed locally from what's in Room, per phase type -- used by the
     * Phase Types screen instead of the server's usage_count (which is
     * only present in its own list response). */
    fun observeUsageCounts(): Flow<List<TypeUsageCount>> = dao.observeUsageCounts()

    suspend fun create(
        plantLocalId: Long,
        phaseTypeLocalId: Long,
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int,
        notes: String?,
    ): Result<PlantPhaseWindowEntity> = runCatching {
        val entity = PlantPhaseWindowEntity(
            plantLocalId = plantLocalId,
            phaseTypeLocalId = phaseTypeLocalId,
            startMonth = startMonth,
            startDay = startDay,
            endMonth = endMonth,
            endDay = endDay,
            notes = notes,
            pendingSync = true,
        )
        val localId = dao.upsert(entity)
        outboxRepository.enqueueCreate(entityType, localId, WindowPayload(phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes))
        entity.copy(localId = localId)
    }

    suspend fun update(
        localId: Long,
        phaseTypeLocalId: Long,
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int,
        notes: String?,
    ): Result<PlantPhaseWindowEntity> = runCatching {
        val existing = dao.getByLocalId(localId) ?: error("Phase window $localId not found locally")
        val updated = existing.copy(
            phaseTypeLocalId = phaseTypeLocalId,
            startMonth = startMonth,
            startDay = startDay,
            endMonth = endMonth,
            endDay = endDay,
            notes = notes,
            dirty = existing.serverId != null,
            pendingSync = true,
        )
        dao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, localId, WindowPayload(phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes))
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
        val phaseTypeLocalIdByServerId = phaseTypeDao.getAllOnce()
            .mapNotNull { t -> t.serverId?.let { it to t.localId } }
            .toMap()

        val remote = api.getPhaseWindows(plantServerId)
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto ->
            val phaseTypeLocalId = phaseTypeLocalIdByServerId[dto.phaseTypeId] ?: return@forEach
            if (dao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto, plantLocalId, phaseTypeLocalId)
        }
        dao.getSyncedServerIdsForPlant(plantLocalId).filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        val window = dao.getByLocalId(op.entityLocalId) ?: return OutboxResult.Success // already gone locally, nothing to do
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, WindowPayload::class.java)
                val plantServerId = plantDao.getByLocalId(window.plantLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val phaseTypeServerId = phaseTypeDao.getByLocalId(payload.phaseTypeLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = api.createPhaseWindow(
                    plantServerId,
                    CreatePhaseWindowRequest(phaseTypeServerId, payload.startMonth, payload.startDay, payload.endMonth, payload.endDay, payload.notes),
                )
                upsertFromDto(dto, window.plantLocalId, payload.phaseTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = window.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, WindowPayload::class.java)
                val phaseTypeServerId = phaseTypeDao.getByLocalId(payload.phaseTypeLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = api.updatePhaseWindow(
                    serverId,
                    CreatePhaseWindowRequest(phaseTypeServerId, payload.startMonth, payload.startDay, payload.endMonth, payload.endDay, payload.notes),
                )
                upsertFromDto(dto, window.plantLocalId, payload.phaseTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> {
                window.serverId?.let { api.deletePhaseWindow(it) }
                dao.deleteByLocalId(op.entityLocalId)
                OutboxResult.Success
            }
            else -> error("Phase windows only support CREATE/UPDATE/DELETE")
        }
    }

    private suspend fun upsertFromDto(
        dto: PhaseWindowDto,
        plantLocalId: Long,
        phaseTypeLocalId: Long,
        preserveLocalId: Long? = null,
    ): PlantPhaseWindowEntity {
        val existing = preserveLocalId?.let { dao.getByLocalId(it) } ?: dao.getByServerId(dto.id)
        val entity = PlantPhaseWindowEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            plantLocalId = plantLocalId,
            phaseTypeLocalId = phaseTypeLocalId,
            startMonth = dto.startMonth,
            startDay = dto.startDay,
            endMonth = dto.endMonth,
            endDay = dto.endDay,
            notes = dto.notes,
        )
        val newLocalId = dao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }
}
