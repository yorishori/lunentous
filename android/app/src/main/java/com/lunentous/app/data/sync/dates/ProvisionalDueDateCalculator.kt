package com.lunentous.app.data.sync.dates

import com.lunentous.app.data.local.dao.OverridePeriodDao
import com.lunentous.app.data.local.dao.ReminderRuleDao
import com.lunentous.app.data.local.dao.ReminderStateDao
import com.lunentous.app.data.local.dao.TimelineEventDao
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderStateSource

/**
 * Direct Kotlin port of server/src/lib/recompute.ts's
 * recomputeReminderState, run locally for instant feedback after an
 * offline write that would trigger the server's own recompute (a rule/
 * override-period change, or a timeline event tagged with a reminder
 * type). Writes ReminderStateEntity with source = LOCAL_PROVISIONAL --
 * unconditionally overwritten by the next SERVER-sourced pull, per the
 * Android plan's Conflict policy. Never enqueues anything itself; callers
 * are responsible for the outbox op that eventually reaches the server.
 */
class ProvisionalDueDateCalculator(
    private val ruleDao: ReminderRuleDao,
    private val periodDao: OverridePeriodDao,
    private val eventDao: TimelineEventDao,
    private val reminderStateDao: ReminderStateDao,
) {
    suspend fun recompute(plantLocalId: Long, reminderTypeLocalId: Long) {
        val rule = ruleDao.getByPlantAndType(plantLocalId, reminderTypeLocalId)
        if (rule == null) {
            reminderStateDao.deleteByPlantAndType(plantLocalId, reminderTypeLocalId)
            return
        }

        val latestEvent = eventDao.getMostRecentByPlantAndType(plantLocalId, reminderTypeLocalId)
        // Never logged: baseline is the rule's creation date, so a new rule
        // has an immediate first due date instead of sitting undefined --
        // mirrors recompute.ts exactly. createdAt is always a real date by
        // the time this runs (see ReminderRuleRepository.create()).
        val baselineDate = latestEvent?.eventDate ?: rule.createdAt.take(10)

        val periods = periodDao.getByRuleOnce(rule.localId)
        val interval = DateMath.resolveInterval(rule.defaultIntervalDays, periods, baselineDate)
        // Never logged: the baseline date itself is due (today counts as
        // day one of the interval), not baseline + interval -- mirrors
        // recompute.ts's identical fix. Annual fixed-date rules (interval
        // null, annualMonth/annualDay set) step to the next occurrence of
        // that calendar date instead.
        val annualMonth = rule.annualMonth
        val annualDay = rule.annualDay
        val dueDate = when {
            interval != null && latestEvent != null -> DateMath.addDays(baselineDate, interval)
            interval != null -> baselineDate
            annualMonth != null && annualDay != null -> DateMath.nextAnnualOccurrence(baselineDate, annualMonth, annualDay, strictlyAfter = latestEvent != null)
            else -> null
        }

        val existing = reminderStateDao.getByPlantAndType(plantLocalId, reminderTypeLocalId)
        reminderStateDao.upsert(
            ReminderStateEntity(
                localId = existing?.localId ?: 0,
                serverId = existing?.serverId,
                plantLocalId = plantLocalId,
                reminderTypeLocalId = reminderTypeLocalId,
                dueDate = dueDate,
                notified = false,
                source = ReminderStateSource.LOCAL_PROVISIONAL,
                computedAt = System.currentTimeMillis(),
            ),
        )
    }
}
