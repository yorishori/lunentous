package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.OverridePeriodDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderRuleDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.dao.TypeUsageCount
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import com.lunentous.app.data.local.entity.ReminderRuleEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreateReminderRuleRequest
import com.lunentous.app.data.remote.dto.OverridePeriodDto
import com.lunentous.app.data.remote.dto.ReminderRuleDto
import com.lunentous.app.data.remote.dto.UpdateReminderRuleRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReminderRuleWithPeriods(
    val rule: ReminderRuleEntity,
    val overridePeriods: List<OverridePeriodEntity>,
)

/**
 * Note: creating/editing a rule changes the relevant reminder_states row
 * server-side (recompute), but the mutation response here doesn't include
 * it (checked against the actual route handler) -- callers should re-run
 * ReminderStateRepository.pullSyncForPlant() afterward. Left as a
 * ViewModel-layer orchestration rather than a repo-to-repo dependency.
 *
 * plantDao/reminderTypeDao are held only to resolve a plantLocalId/
 * reminderTypeLocalId to its serverId when a network call needs one --
 * callers never have to look this up or pass it in themselves.
 */
class ReminderRuleRepository(
    private val ruleDao: ReminderRuleDao,
    private val periodDao: OverridePeriodDao,
    private val plantDao: PlantDao,
    private val reminderTypeDao: ReminderTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeByPlant(plantLocalId: Long): Flow<List<ReminderRuleWithPeriods>> =
        ruleDao.observeByPlant(plantLocalId).map { rules ->
            rules.map { rule -> ReminderRuleWithPeriods(rule, periodDao.getByRuleOnce(rule.localId)) }
        }

    /** Across every plant, with override periods -- used by the Calendar
     * screen to project future due dates. */
    fun observeAll(): Flow<List<ReminderRuleWithPeriods>> =
        ruleDao.observeAll().map { rules ->
            rules.map { rule -> ReminderRuleWithPeriods(rule, periodDao.getByRuleOnce(rule.localId)) }
        }

    /** Computed locally from what's in Room, per reminder type -- used by
     * the Reminder Types screen instead of the server's usage_count (which
     * is only present in its own list response). */
    fun observeUsageCounts(): Flow<List<TypeUsageCount>> = ruleDao.observeUsageCounts()

    suspend fun create(
        plantLocalId: Long,
        reminderTypeLocalId: Long,
        defaultIntervalDays: Int?,
        overridePeriods: List<OverridePeriodEntity>,
    ): Result<ReminderRuleWithPeriods> = runCatching {
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId
        val reminderTypeServerId = reminderTypeDao.getByLocalId(reminderTypeLocalId)?.serverId

        if (sessionStore.hasSession() && plantServerId != null && reminderTypeServerId != null) {
            val dto = api.createReminderRule(
                plantServerId,
                CreateReminderRuleRequest(reminderTypeServerId, defaultIntervalDays, overridePeriods.toDtos()),
            )
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        } else {
            val rule = ReminderRuleEntity(
                plantLocalId = plantLocalId,
                reminderTypeLocalId = reminderTypeLocalId,
                defaultIntervalDays = defaultIntervalDays,
            )
            val ruleLocalId = ruleDao.upsert(rule)
            periodDao.insertAll(overridePeriods.map { it.copy(reminderRuleLocalId = ruleLocalId) })
            ReminderRuleWithPeriods(rule.copy(localId = ruleLocalId), periodDao.getByRuleOnce(ruleLocalId))
        }
    }

    suspend fun update(
        ruleLocalId: Long,
        defaultIntervalDays: Int?,
        overridePeriods: List<OverridePeriodEntity>,
    ): Result<ReminderRuleWithPeriods> = runCatching {
        val existing = ruleDao.getByLocalId(ruleLocalId) ?: error("Reminder rule $ruleLocalId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = api.updateReminderRule(
                existing.serverId,
                UpdateReminderRuleRequest(defaultIntervalDays, overridePeriods.toDtos()),
            )
            upsertFromDto(dto, existing.plantLocalId, existing.reminderTypeLocalId, preserveLocalId = ruleLocalId)
        } else {
            val updated = existing.copy(defaultIntervalDays = defaultIntervalDays, dirty = existing.serverId != null)
            ruleDao.upsert(updated)
            periodDao.deleteByRule(ruleLocalId)
            periodDao.insertAll(overridePeriods.map { it.copy(reminderRuleLocalId = ruleLocalId) })
            ReminderRuleWithPeriods(updated, periodDao.getByRuleOnce(ruleLocalId))
        }
    }

    suspend fun delete(ruleLocalId: Long): Result<Unit> = runCatching {
        val existing = ruleDao.getByLocalId(ruleLocalId) ?: return@runCatching
        if (sessionStore.hasSession() && existing.serverId != null) {
            api.deleteReminderRule(existing.serverId)
        }
        periodDao.deleteByRule(ruleLocalId)
        ruleDao.deleteByLocalId(ruleLocalId)
    }

    suspend fun pullSyncForPlant(plantLocalId: Long) {
        if (!sessionStore.hasSession()) return
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId ?: return
        val reminderTypeLocalIdByServerId = reminderTypeDao.getAllOnce()
            .mapNotNull { t -> t.serverId?.let { it to t.localId } }
            .toMap()

        val remote = api.getReminderRules(plantServerId)
        val remoteIds = remote.map { it.id }.toSet()
        remote.forEach { dto ->
            val reminderTypeLocalId = reminderTypeLocalIdByServerId[dto.reminderTypeId] ?: return@forEach
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
        ruleDao.getSyncedServerIdsForPlant(plantLocalId).filterNot { it in remoteIds }.forEach { serverId ->
            ruleDao.deleteByServerId(serverId)
        }
    }

    private suspend fun upsertFromDto(
        dto: ReminderRuleDto,
        plantLocalId: Long,
        reminderTypeLocalId: Long,
        preserveLocalId: Long? = null,
    ): ReminderRuleWithPeriods {
        val existing = preserveLocalId?.let { ruleDao.getByLocalId(it) } ?: ruleDao.getByServerId(dto.id)
        val entity = ReminderRuleEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            plantLocalId = plantLocalId,
            reminderTypeLocalId = reminderTypeLocalId,
            defaultIntervalDays = dto.defaultIntervalDays,
            createdAt = dto.createdAt,
        )
        val ruleLocalId = if (existing != null) existing.localId else ruleDao.upsert(entity)
        if (existing != null) ruleDao.upsert(entity)

        periodDao.deleteByRule(ruleLocalId)
        val periods = dto.overridePeriods.map { p ->
            OverridePeriodEntity(
                serverId = p.id,
                reminderRuleLocalId = ruleLocalId,
                startMonth = p.startMonth,
                startDay = p.startDay,
                endMonth = p.endMonth,
                endDay = p.endDay,
                intervalDays = p.intervalDays,
            )
        }
        periodDao.insertAll(periods)

        return ReminderRuleWithPeriods(entity.copy(localId = ruleLocalId), periodDao.getByRuleOnce(ruleLocalId))
    }

    private fun List<OverridePeriodEntity>.toDtos(): List<OverridePeriodDto> = map {
        OverridePeriodDto(
            startMonth = it.startMonth,
            startDay = it.startDay,
            endMonth = it.endMonth,
            endDay = it.endDay,
            intervalDays = it.intervalDays,
        )
    }
}
