package com.lunentous.app.data.sync.dates

import com.lunentous.app.data.local.entity.OverridePeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors server/test/dates.test.ts and web/src/lib/dateMath.test.ts --
 * all three platforms independently implement this same date math, and
 * it's already had real bugs (the Feb 29 crash, the due-date off-by-one)
 * that a single platform's tests wouldn't have caught for the others. */
class DateMathTest {
    private fun period(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int, intervalDays: Int?) =
        OverridePeriodEntity(reminderRuleLocalId = 0, startMonth = startMonth, startDay = startDay, endMonth = endMonth, endDay = endDay, intervalDays = intervalDays)

    @Test
    fun `addDays adds whole days within a month`() {
        assertEquals("2026-07-06", DateMath.addDays("2026-07-01", 5))
    }

    @Test
    fun `addDays rolls over a month boundary`() {
        assertEquals("2026-08-02", DateMath.addDays("2026-07-30", 3))
    }

    @Test
    fun `addDays rolls over a year boundary`() {
        assertEquals("2027-01-02", DateMath.addDays("2026-12-30", 3))
    }

    @Test
    fun `dateInRange matches a normal non-wrapping range`() {
        assertEquals(true, DateMath.dateInRange("2026-06-15", 6, 1, 6, 30))
        assertEquals(false, DateMath.dateInRange("2026-07-01", 6, 1, 6, 30))
    }

    @Test
    fun `dateInRange handles a year-wrapping range`() {
        assertEquals(true, DateMath.dateInRange("2026-12-15", 11, 1, 2, 28))
        assertEquals(true, DateMath.dateInRange("2026-01-15", 11, 1, 2, 28))
        assertEquals(false, DateMath.dateInRange("2026-06-15", 11, 1, 2, 28))
    }

    @Test
    fun `resolveInterval returns the default when no override period matches`() {
        assertEquals(7, DateMath.resolveInterval(7, emptyList(), "2026-07-01"))
    }

    @Test
    fun `resolveInterval returns an override period's interval when the date falls inside it`() {
        val periods = listOf(period(12, 1, 2, 28, 14))
        assertEquals(14, DateMath.resolveInterval(7, periods, "2026-01-15"))
        assertEquals(7, DateMath.resolveInterval(7, periods, "2026-07-15"))
    }

    @Test
    fun `resolveInterval returns null when the matching period is paused`() {
        val periods = listOf(period(1, 1, 12, 31, null))
        assertNull(DateMath.resolveInterval(7, periods, "2026-07-15"))
    }

    @Test
    fun `nextAnnualOccurrence returns this year's date when it's still ahead`() {
        assertEquals("2026-06-15", DateMath.nextAnnualOccurrence("2026-01-01", 6, 15, false))
    }

    @Test
    fun `nextAnnualOccurrence rolls to next year when this year's date has passed`() {
        assertEquals("2027-06-15", DateMath.nextAnnualOccurrence("2026-08-01", 6, 15, false))
    }

    @Test
    fun `nextAnnualOccurrence rolls to next year when the date matches exactly and strictlyAfter is true`() {
        assertEquals("2027-06-15", DateMath.nextAnnualOccurrence("2026-06-15", 6, 15, true))
    }

    @Test
    fun `nextAnnualOccurrence clamps Feb 29 to Feb 28 on a non-leap target year instead of throwing`() {
        // The exact bug fixed this session: LocalDate.of(2026, 2, 29) throws
        // DateTimeException since 2026 isn't a leap year.
        assertEquals("2026-02-28", DateMath.nextAnnualOccurrence("2026-01-01", 2, 29, false))
    }

    @Test
    fun `nextAnnualOccurrence resolves Feb 29 correctly on a leap target year`() {
        assertEquals("2028-02-29", DateMath.nextAnnualOccurrence("2028-01-01", 2, 29, false))
    }

    @Test
    fun `projectOccurrencesInRange projects every daily occurrence within the window`() {
        val results = DateMath.projectOccurrencesInRange("2026-07-01", 1, emptyList(), "2026-07-01", "2026-07-05")
        assertEquals(listOf("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05"), results)
    }

    @Test
    fun `projectOccurrencesInRange stops once the interval resolves to paused`() {
        val periods = listOf(period(1, 1, 12, 31, null))
        val results = DateMath.projectOccurrencesInRange("2026-07-01", 7, periods, "2026-07-01", "2026-12-31")
        assertEquals(listOf("2026-07-01"), results)
    }

    @Test
    fun `projectOccurrencesInRange projects annual occurrences a year apart`() {
        val results = DateMath.projectOccurrencesInRange(
            "2026-06-15", null, emptyList(), "2026-01-01", "2028-12-31", 500, 6, 15,
        )
        assertEquals(listOf("2026-06-15", "2027-06-15", "2028-06-15"), results)
    }
}
