package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderStateDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
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
 * the outbox.
 *
 * plantDao/reminderTypeDao are held only to resolve local<->server ID maps
 * internally -- callers never build or pass these themselves.
 */
class ReminderStateRepository(
    private val dao: ReminderStateDao,
    private val plantDao: PlantDao,
    private val reminderTypeDao: ReminderTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByPlant(plantLocalId: Long): Flow<List<ReminderStateEntity>> = dao.observeByPlant(plantLocalId)

    fun observeAll(): Flow<List<ReminderStateEntity>> = dao.observeAll()

    suspend fun getAllOnce(): List<ReminderStateEntity> = dao.getAllOnce()

    /** Global pull -- used by the dashboard's overdue/next-tasks lists. */
    suspend fun pullSyncAll() {
        if (!sessionStore.hasSession()) return
        val plantLocalIdByServerId = plantDao.getAllOnce().mapNotNull { p -> p.serverId?.let { it to p.localId } }.toMap()
        val reminderTypeLocalIdByServerId = reminderTypeDao.getAllOnce().mapNotNull { t -> t.serverId?.let { it to t.localId } }.toMap()

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
    suspend fun pullSyncForPlant(plantLocalId: Long) {
        if (!sessionStore.hasSession()) return
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId ?: return
        val reminderTypeLocalIdByServerId = reminderTypeDao.getAllOnce().mapNotNull { t -> t.serverId?.let { it to t.localId } }.toMap()

        val remote = api.getReminderStatesForPlant(plantServerId)
        remote.forEach { dto ->
            val reminderTypeLocalId = reminderTypeLocalIdByServerId[dto.reminderTypeId] ?: return@forEach
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
    }

    /** Local-only read, no network -- called after a pull has already
     * refreshed reminder_states, so this just finds what's due for
     * ReminderNotifier to post without a second round trip. */
    suspend fun getDueUnnotified(today: String): List<ReminderStateEntity> = dao.getDueUnnotified(today)

    /** Tells the server this reminder has been surfaced to the user (so it
     * stops showing up in future due_before_or_on/notified polls elsewhere)
     * and mirrors that locally so this device's own next poll skips it too,
     * even before the next full pull. */
    suspend fun markNotified(state: ReminderStateEntity) {
        if (sessionStore.hasSession()) {
            state.serverId?.let { api.markNotified(it) }
        }
        dao.markNotifiedLocally(state.localId)
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
