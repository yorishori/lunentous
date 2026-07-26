// All domain dates are plain calendar dates ("YYYY-MM-DD"), no timezone
// component -- per spec_v1.md §9. "Today" is the server process's local date.

export type ISODate = string;

export function todayLocalDate(): ISODate {
  const now = new Date();
  return toISODate(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

function parseISODate(date: ISODate): { y: number; m: number; d: number } {
  const [y, m, d] = date.split("-").map(Number);
  return { y, m, d };
}

function toISODate(y: number, m: number, d: number): ISODate {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/** Adds (or subtracts, if negative) whole days to a calendar date. */
export function addDays(date: ISODate, days: number): ISODate {
  const { y, m, d } = parseISODate(date);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  return toISODate(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}

/** b - a, in whole days. */
export function daysBetween(a: ISODate, b: ISODate): number {
  const pa = parseISODate(a);
  const pb = parseISODate(b);
  const ta = Date.UTC(pa.y, pa.m - 1, pa.d);
  const tb = Date.UTC(pb.y, pb.m - 1, pb.d);
  return Math.round((tb - ta) / 86_400_000);
}

/** spec §4.1 date_in_range -- handles year-wrap ranges (e.g. Nov 1 -> Feb 28). */
export function dateInRange(
  date: ISODate,
  startMonth: number,
  startDay: number,
  endMonth: number,
  endDay: number
): boolean {
  const { m, d } = parseISODate(date);
  const D = m * 100 + d;
  const start = startMonth * 100 + startDay;
  const end = endMonth * 100 + endDay;
  if (start <= end) {
    return D >= start && D <= end;
  }
  return D >= start || D <= end;
}

export interface OverridePeriodLike {
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  interval_days: number | null;
}

export interface ReminderRuleLike {
  default_interval_days: number | null;
}

/** spec §4.1 resolve_interval. */
export function resolveInterval(
  rule: ReminderRuleLike,
  periods: OverridePeriodLike[],
  date: ISODate
): number | null {
  for (const period of periods) {
    if (dateInRange(date, period.start_month, period.start_day, period.end_month, period.end_day)) {
      return period.interval_days;
    }
  }
  return rule.default_interval_days;
}

/** Validates month/day ranges are within calendar bounds (spec §9). Does not
 * check calendar-validity beyond that (e.g. Feb 30 is allowed, per spec). */
export function isValidMonthDay(month: number, day: number): boolean {
  return Number.isInteger(month) && month >= 1 && month <= 12 && Number.isInteger(day) && day >= 1 && day <= 31;
}

/** spec §9: override_periods for the same reminder_rule_id must not overlap.
 * Since ranges may wrap the year boundary, we test overlap by checking
 * whether either period's start point falls inside the other's range, plus
 * the symmetric case -- sufficient for two closed (possibly wrapping) ranges. */
export function periodsOverlap(a: OverridePeriodLike, b: OverridePeriodLike): boolean {
  const aStart = toProbeDate(a.start_month, a.start_day);
  const bStart = toProbeDate(b.start_month, b.start_day);
  return (
    dateInRange(aStart, b.start_month, b.start_day, b.end_month, b.end_day) ||
    dateInRange(bStart, a.start_month, a.start_day, a.end_month, a.end_day)
  );
}

function toProbeDate(month: number, day: number): ISODate {
  // dateInRange only ever splits this string and compares month/day as
  // integers -- it never constructs a real Date -- so an out-of-range day
  // (e.g. Feb 30) is safe to pass through unclamped.
  return `2001-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}
