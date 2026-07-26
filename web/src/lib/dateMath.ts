// Client-side mirror of server/src/lib/dates.ts, used only to compute the
// calendar view's dashed *projected* future occurrences (spec §6) -- these
// are display-only and never written back to the database.

export type ISODate = string;

function parseISODate(date: ISODate): { y: number; m: number; d: number } {
  const [y, m, d] = date.split("-").map(Number);
  return { y, m, d };
}

export function addDays(date: ISODate, days: number): ISODate {
  const { y, m, d } = parseISODate(date);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(dt.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

export function dateInRange(date: ISODate, startMonth: number, startDay: number, endMonth: number, endDay: number): boolean {
  const { m, d } = parseISODate(date);
  const D = m * 100 + d;
  const start = startMonth * 100 + startDay;
  const end = endMonth * 100 + endDay;
  if (start <= end) return D >= start && D <= end;
  return D >= start || D <= end;
}

export interface OverridePeriodLike {
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  interval_days: number | null;
}

export function resolveInterval(
  defaultIntervalDays: number | null,
  periods: OverridePeriodLike[],
  date: ISODate
): number | null {
  for (const period of periods) {
    if (dateInRange(date, period.start_month, period.start_day, period.end_month, period.end_day)) {
      return period.interval_days;
    }
  }
  return defaultIntervalDays;
}

/** Repeatedly applies interval resolution forward from a materialized due
 * date, assuming on-time completion each time (spec §6 calendar view),
 * collecting every occurrence that lands within [rangeStart, rangeEnd] --
 * including the starting due date itself, if it's in range. Display only;
 * never written back to the database. `maxIterations` bounds the walk when
 * the due date is far outside the requested range. */
export function projectOccurrencesInRange(
  fromDueDate: ISODate,
  defaultIntervalDays: number | null,
  periods: OverridePeriodLike[],
  rangeStart: ISODate,
  rangeEnd: ISODate,
  maxIterations = 500
): ISODate[] {
  const results: ISODate[] = [];
  let current = fromDueDate;
  for (let i = 0; i < maxIterations; i++) {
    if (current > rangeEnd) break;
    if (current >= rangeStart) results.push(current);
    const interval = resolveInterval(defaultIntervalDays, periods, current);
    if (interval == null) break;
    current = addDays(current, interval);
  }
  return results;
}
