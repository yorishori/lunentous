package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.ReminderStateDao
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderStateSource
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.ReminderStateDto
import kotlinx.coroutines.flow.Flow

/** reminder_states is always server-authoritative once synced -- see the
 * Android plan's Conflict policy. This repository never lets a caller write
 * a SERVER-sourced row directly; only pullSync* does that. Local-provisional
 * writes (computed immediately after an offline reminder-affecting edit)
 * belong to ProvisionalDueDateCalculator, added in a later phase alongside
 * the outbox. */
class ReminderStateRepository(
    private val dao: ReminderStateDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByPlant(plantLocalId: Long): Flow<List<ReminderStateEntity>> = dao.observeByPlant(plantLocalId)

    fun observeAll(): Flow<List<ReminderStateEntity>> = dao.observeAll()

    /** Global pull -- used by the dashboard's overdue/next-tasks lists. */
    suspend fun pullSyncAll(plantLocalIdByServerId: Map<Long, Long>, reminderTypeLocalIdByServerId: Map<Long, Long>) {
        if (!sessionStore.hasSession()) return
        val remote = api.getReminderStates()
        remote.forEach { dto ->
            val plantLocalId = plantLocalIdByServerId[dto.plantId] ?: return@forEach
            val reminderTypeLocalId = reminderTypeLocalIdByServerId[dto.reminderTypeId] ?: return@forEach
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
    }

    /** Targeted re-fetch for one plant -- call after any mutation that
     * could have triggered server-side recompute (see ReminderRuleRepository
     * and TimelineRepository's docs), since mutation responses don't
     * include the recomputed state. */
    suspend fun pullSyncForPlant(plantLocalId: Long, plantServerId: Long, reminderTypeLocalIdByServerId: Map<Long, Long>) {
        if (!sessionStore.hasSession()) return
        val remote = api.getReminderStatesForPlant(plantServerId)
        remote.forEach { dto ->
            val reminderTypeLocalId = reminderTypeLocalIdByServerId[dto.reminderTypeId] ?: return@forEach
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
    }

    private suspend fun upsertFromDto(dto: ReminderStateDto, plantLocalId: Long, reminderTypeLocalId: Long) {
        val existing = dao.getByPlantAndType(plantLocalId, reminderTypeLocalId)
        dao.upsert(
            ReminderStateEntity(
                localId = existing?.localId ?: 0,
                serverId = dto.id,
                plantLocalId = plantLocalId,
                reminderTypeLocalId = reminderTypeLocalId,
                dueDate = dto.dueDate,
                notified = dto.notified == 1,
                source = ReminderStateSource.SERVER,
                computedAt = System.currentTimeMillis(),
            )
        )
    }
}
