package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhaseTypeDao
import com.lunentous.app.data.local.dao.PhaseWindowDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreatePhaseWindowRequest
import com.lunentous.app.data.remote.dto.PhaseWindowDto
import kotlinx.coroutines.flow.Flow

class PhaseWindowRepository(
    private val dao: PhaseWindowDao,
    private val plantDao: PlantDao,
    private val phaseTypeDao: PhaseTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByPlant(plantLocalId: Long): Flow<List<PlantPhaseWindowEntity>> = dao.observeByPlant(plantLocalId)

    suspend fun create(
        plantLocalId: Long,
        phaseTypeLocalId: Long,
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int,
        notes: String?,
    ): Result<PlantPhaseWindowEntity> = runCatching {
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId
        val phaseTypeServerId = phaseTypeDao.getByLocalId(phaseTypeLocalId)?.serverId

        if (sessionStore.hasSession() && plantServerId != null && phaseTypeServerId != null) {
            val dto = api.createPhaseWindow(
                plantServerId,
                CreatePhaseWindowRequest(phaseTypeServerId, startMonth, startDay, endMonth, endDay, notes),
            )
            upsertFromDto(dto, plantLocalId, phaseTypeLocalId)
        } else {
            val entity = PlantPhaseWindowEntity(
                plantLocalId = plantLocalId,
                phaseTypeLocalId = phaseTypeLocalId,
                startMonth = startMonth,
                startDay = startDay,
                endMonth = endMonth,
                endDay = endDay,
                notes = notes,
            )
            entity.copy(localId = dao.upsert(entity))
        }
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
        val phaseTypeServerId = phaseTypeDao.getByLocalId(phaseTypeLocalId)?.serverId
        if (sessionStore.hasSession() && existing.serverId != null && phaseTypeServerId != null) {
            val dto = api.updatePhaseWindow(
                existing.serverId,
                CreatePhaseWindowRequest(phaseTypeServerId, startMonth, startDay, endMonth, endDay, notes),
            )
            upsertFromDto(dto, existing.plantLocalId, phaseTypeLocalId, preserveLocalId = localId)
        } else {
            val updated = existing.copy(
                phaseTypeLocalId = phaseTypeLocalId,
                startMonth = startMonth,
                startDay = startDay,
                endMonth = endMonth,
                endDay = endDay,
                notes = notes,
                dirty = existing.serverId != null,
            )
            dao.upsert(updated)
            updated
        }
    }

    suspend fun delete(localId: Long): Result<Unit> = runCatching {
        val existing = dao.getByLocalId(localId) ?: return@runCatching
        if (sessionStore.hasSession() && existing.serverId != null) {
            api.deletePhaseWindow(existing.serverId)
        }
        dao.deleteByLocalId(localId)
    }

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
            upsertFromDto(dto, plantLocalId, phaseTypeLocalId)
        }
        dao.getSyncedServerIdsForPlant(plantLocalId).filterNot { it in remoteIds }.forEach { dao.deleteByServerId(it) }
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
