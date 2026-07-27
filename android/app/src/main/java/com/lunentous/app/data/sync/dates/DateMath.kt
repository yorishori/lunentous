package com.lunentous.app.data.sync.dates

import com.lunentous.app.data.local.entity.OverridePeriodEntity
import java.time.LocalDate

/**
 * Direct Kotlin port of web/src/lib/dateMath.ts, itself a client-side
 * mirror of server/src/lib/dates.ts. Display-only: used by the Calendar
 * screen to project future reminder occurrences and shade phase-window
 * date ranges. Never written back to Room or the server -- the server's
 * own recompute is always authoritative for the actual reminder_states row
 * (see the Android plan's Conflict policy).
 */
object DateMath {
    fun addDays(date: String, days: Int): String = LocalDate.parse(date).plusDays(days.toLong()).toString()

    /** Handles year-wrap ranges (e.g. Nov 1 -- Feb 28) the same way the
     * server's resolveInterval does: if start > end, the range is treated
     * as wrapping across the new year. */
    fun dateInRange(date: String, startMonth: Int, startDay: Int, endMonth: Int, endDay: Int): Boolean {
        val parsed = LocalDate.parse(date)
        val d = parsed.monthValue * 100 + parsed.dayOfMonth
        val start = startMonth * 100 + startDay
        val end = endMonth * 100 + endDay
        return if (start <= end) d in start..end else d >= start || d <= end
    }

    fun resolveInterval(defaultIntervalDays: Int?, periods: List<OverridePeriodEntity>, date: String): Int? {
        for (period in periods) {
            if (dateInRange(date, period.startMonth, period.startDay, period.endMonth, period.endDay)) {
                return period.intervalDays
            }
        }
        return defaultIntervalDays
    }

    /** Repeatedly applies interval resolution forward from a materialized
     * due date, assuming on-time completion each time, collecting every
     * occurrence landing within [rangeStart, rangeEnd] -- including the
     * starting due date itself if it's in range. `maxIterations` bounds the
     * walk when the due date is far outside the requested range. */
    fun projectOccurrencesInRange(
        fromDueDate: String,
        defaultIntervalDays: Int?,
        periods: List<OverridePeriodEntity>,
        rangeStart: String,
        rangeEnd: String,
        maxIterations: Int = 500,
    ): List<String> {
        val results = mutableListOf<String>()
        var current = fromDueDate
        repeat(maxIterations) {
            if (current > rangeEnd) return results
            if (current >= rangeStart) results.add(current)
            val interval = resolveInterval(defaultIntervalDays, periods, current) ?: return results
            current = addDays(current, interval)
        }
        return results
    }
}
