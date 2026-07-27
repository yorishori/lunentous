package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.OverridePeriodDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderRuleDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.dao.TypeUsageCount
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import com.lunentous.app.data.local.entity.ReminderRuleEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.CreateReminderRuleRequest
import com.lunentous.app.data.remote.dto.OverridePeriodDto
import com.lunentous.app.data.remote.dto.ReminderRuleDto
import com.lunentous.app.data.remote.dto.UpdateReminderRuleRequest
import com.lunentous.app.data.sync.dates.ProvisionalDueDateCalculator
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReminderRuleWithPeriods(
    val rule: ReminderRuleEntity,
    val overridePeriods: List<OverridePeriodEntity>,
)

private data class PeriodPayload(val startMonth: Int, val startDay: Int, val endMonth: Int, val endDay: Int, val intervalDays: Int?)
private data class RulePayload(val reminderTypeLocalId: Long, val defaultIntervalDays: Int?, val periods: List<PeriodPayload>)

/**
 * Note: creating/editing/deleting a rule changes the relevant
 * reminder_states row server-side (recompute) -- the local-provisional
 * equivalent runs synchronously here via ProvisionalDueDateCalculator for
 * instant feedback, and the outbox op's eventual success/reconciliation is
 * followed up by a targeted ReminderStateRepository.pullSyncForPlant() at
 * the ViewModel layer (mutation responses don't include the recomputed
 * state -- checked against the actual route handler).
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
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
    private val provisionalCalculator: ProvisionalDueDateCalculator,
) : OutboxHandler {
    override val entityType = OutboxEntityType.REMINDER_RULE

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
        val rule = ReminderRuleEntity(
            plantLocalId = plantLocalId,
            reminderTypeLocalId = reminderTypeLocalId,
            defaultIntervalDays = defaultIntervalDays,
            // Stamped locally so ProvisionalDueDateCalculator's baseline
            // (rule.createdAt when no timeline event exists yet) is never
            // blank -- mirrors what the server would assign at create time.
            createdAt = LocalDate.now().toString(),
            pendingSync = true,
        )
        val ruleLocalId = ruleDao.upsert(rule)
        periodDao.insertAll(overridePeriods.map { it.copy(reminderRuleLocalId = ruleLocalId) })
        outboxRepository.enqueueCreate(entityType, ruleLocalId, RulePayload(reminderTypeLocalId, defaultIntervalDays, overridePeriods.toPayloads()))
        provisionalCalculator.recompute(plantLocalId, reminderTypeLocalId)
        ReminderRuleWithPeriods(rule.copy(localId = ruleLocalId), periodDao.getByRuleOnce(ruleLocalId))
    }

    suspend fun update(
        ruleLocalId: Long,
        defaultIntervalDays: Int?,
        overridePeriods: List<OverridePeriodEntity>,
    ): Result<ReminderRuleWithPeriods> = runCatching {
        val existing = ruleDao.getByLocalId(ruleLocalId) ?: error("Reminder rule $ruleLocalId not found locally")
        val updated = existing.copy(defaultIntervalDays = defaultIntervalDays, dirty = existing.serverId != null, pendingSync = true)
        ruleDao.upsert(updated)
        periodDao.deleteByRule(ruleLocalId)
        periodDao.insertAll(overridePeriods.map { it.copy(reminderRuleLocalId = ruleLocalId) })
        outboxRepository.enqueueUpdate(entityType, ruleLocalId, RulePayload(existing.reminderTypeLocalId, defaultIntervalDays, overridePeriods.toPayloads()))
        provisionalCalculator.recompute(existing.plantLocalId, existing.reminderTypeLocalId)
        ReminderRuleWithPeriods(updated, periodDao.getByRuleOnce(ruleLocalId))
    }

    suspend fun delete(ruleLocalId: Long): Result<Unit> = runCatching {
        val existing = ruleDao.getByLocalId(ruleLocalId) ?: return@runCatching
        val localOnly = outboxRepository.enqueueDelete(entityType, ruleLocalId)
        if (localOnly) {
            periodDao.deleteByRule(ruleLocalId)
            ruleDao.deleteByLocalId(ruleLocalId)
        } else {
            // Tombstone rather than hard-delete -- OutboxProcessor still
            // needs this row's serverId when the DELETE op actually runs.
            ruleDao.upsert(existing.copy(deleted = true, pendingSync = true))
        }
        provisionalCalculator.recompute(existing.plantLocalId, existing.reminderTypeLocalId)
    }

    /** Skips rows with unpushed local edits -- see PlantRepository.pullSync
     * for why. */
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
            if (ruleDao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
        ruleDao.getSyncedServerIdsForPlant(plantLocalId).filterNot { it in remoteIds }.forEach { serverId ->
            ruleDao.deleteByServerId(serverId)
        }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        val rule = ruleDao.getByLocalId(op.entityLocalId) ?: return OutboxResult.Success // already gone locally, nothing to do
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, RulePayload::class.java)
                val plantServerId = plantDao.getByLocalId(rule.plantLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val reminderTypeServerId = reminderTypeDao.getByLocalId(payload.reminderTypeLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val dto = api.createReminderRule(plantServerId, CreateReminderRuleRequest(reminderTypeServerId, payload.defaultIntervalDays, payload.periods.toDtos()))
                upsertFromDto(dto, rule.plantLocalId, payload.reminderTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = rule.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, RulePayload::class.java)
                val dto = api.updateReminderRule(serverId, UpdateReminderRuleRequest(payload.defaultIntervalDays, payload.periods.toDtos()))
                upsertFromDto(dto, rule.plantLocalId, rule.reminderTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> {
                rule.serverId?.let { api.deleteReminderRule(it) }
                periodDao.deleteByRule(op.entityLocalId)
                ruleDao.deleteByLocalId(op.entityLocalId)
                OutboxResult.Success
            }
            else -> error("Reminder rules only support CREATE/UPDATE/DELETE")
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

    private fun List<OverridePeriodEntity>.toPayloads(): List<PeriodPayload> = map {
        PeriodPayload(it.startMonth, it.startDay, it.endMonth, it.endDay, it.intervalDays)
    }

    private fun List<PeriodPayload>.toDtos(): List<OverridePeriodDto> = map {
        OverridePeriodDto(
            startMonth = it.startMonth,
            startDay = it.startDay,
            endMonth = it.endMonth,
            endDay = it.endDay,
            intervalDays = it.intervalDays,
        )
    }
}
