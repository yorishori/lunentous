import { describe, expect, it } from "vitest";
import {
  addDays,
  daysBetween,
  dateInRange,
  isValidMonthDay,
  nextAnnualOccurrence,
  periodsOverlap,
  resolveInterval,
  todayLocalDate,
} from "../src/lib/dates.js";

describe("addDays", () => {
  it("adds whole days within a month", () => {
    expect(addDays("2026-07-01", 5)).toBe("2026-07-06");
  });

  it("rolls over a month boundary", () => {
    expect(addDays("2026-07-30", 3)).toBe("2026-08-02");
  });

  it("rolls over a year boundary", () => {
    expect(addDays("2026-12-30", 3)).toBe("2027-01-02");
  });

  it("subtracts with a negative count", () => {
    expect(addDays("2026-07-05", -10)).toBe("2026-06-25");
  });

  it("handles Feb 29 on a leap year correctly", () => {
    expect(addDays("2028-02-28", 1)).toBe("2028-02-29");
    expect(addDays("2028-02-29", 1)).toBe("2028-03-01");
  });
});

describe("daysBetween", () => {
  it("computes a positive gap", () => {
    expect(daysBetween("2026-07-01", "2026-07-10")).toBe(9);
  });

  it("computes a negative gap when b is before a", () => {
    expect(daysBetween("2026-07-10", "2026-07-01")).toBe(-9);
  });

  it("returns 0 for the same date", () => {
    expect(daysBetween("2026-07-01", "2026-07-01")).toBe(0);
  });
});

describe("todayLocalDate", () => {
  it("returns an ISO date string", () => {
    expect(todayLocalDate()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe("dateInRange", () => {
  it("matches a normal (non-wrapping) range", () => {
    expect(dateInRange("2026-06-15", 6, 1, 6, 30)).toBe(true);
    expect(dateInRange("2026-07-01", 6, 1, 6, 30)).toBe(false);
  });

  it("handles a year-wrapping range (e.g. Nov 1 -> Feb 28)", () => {
    expect(dateInRange("2026-12-15", 11, 1, 2, 28)).toBe(true);
    expect(dateInRange("2026-01-15", 11, 1, 2, 28)).toBe(true);
    expect(dateInRange("2026-06-15", 11, 1, 2, 28)).toBe(false);
  });

  it("is inclusive of both endpoints", () => {
    expect(dateInRange("2026-06-01", 6, 1, 6, 30)).toBe(true);
    expect(dateInRange("2026-06-30", 6, 1, 6, 30)).toBe(true);
  });
});

describe("resolveInterval", () => {
  it("returns the default interval when no override period matches", () => {
    expect(resolveInterval({ default_interval_days: 7 }, [], "2026-07-01")).toBe(7);
  });

  it("returns an override period's interval when the date falls inside it", () => {
    const periods = [{ start_month: 12, start_day: 1, end_month: 2, end_day: 28, interval_days: 14 }];
    expect(resolveInterval({ default_interval_days: 7 }, periods, "2026-01-15")).toBe(14);
    expect(resolveInterval({ default_interval_days: 7 }, periods, "2026-07-15")).toBe(7);
  });

  it("returns null (paused) when the matching period's interval is null", () => {
    const periods = [{ start_month: 1, start_day: 1, end_month: 12, end_day: 31, interval_days: null }];
    expect(resolveInterval({ default_interval_days: 7 }, periods, "2026-07-15")).toBeNull();
  });
});

describe("isValidMonthDay", () => {
  it("accepts valid month/day combinations", () => {
    expect(isValidMonthDay(1, 1)).toBe(true);
    expect(isValidMonthDay(12, 31)).toBe(true);
    expect(isValidMonthDay(2, 30)).toBe(true); // spec: not checked against real calendar validity
  });

  it("rejects out-of-range months/days", () => {
    expect(isValidMonthDay(0, 1)).toBe(false);
    expect(isValidMonthDay(13, 1)).toBe(false);
    expect(isValidMonthDay(1, 0)).toBe(false);
    expect(isValidMonthDay(1, 32)).toBe(false);
  });
});

describe("periodsOverlap", () => {
  it("detects overlapping non-wrapping periods", () => {
    const a = { start_month: 1, start_day: 1, end_month: 3, end_day: 1, interval_days: 7 };
    const b = { start_month: 2, start_day: 1, end_month: 4, end_day: 1, interval_days: 14 };
    expect(periodsOverlap(a, b)).toBe(true);
  });

  it("returns false for disjoint periods", () => {
    const a = { start_month: 1, start_day: 1, end_month: 2, end_day: 1, interval_days: 7 };
    const b = { start_month: 6, start_day: 1, end_month: 7, end_day: 1, interval_days: 14 };
    expect(periodsOverlap(a, b)).toBe(false);
  });

  it("detects overlap with a year-wrapping period", () => {
    const a = { start_month: 11, start_day: 1, end_month: 2, end_day: 28, interval_days: 7 };
    const b = { start_month: 1, start_day: 1, end_month: 1, end_day: 31, interval_days: 14 };
    expect(periodsOverlap(a, b)).toBe(true);
  });
});

describe("nextAnnualOccurrence", () => {
  it("returns this year's date when it's still ahead (not strictlyAfter)", () => {
    expect(nextAnnualOccurrence("2026-01-01", 6, 15, false)).toBe("2026-06-15");
  });

  it("rolls to next year when this year's date has already passed", () => {
    expect(nextAnnualOccurrence("2026-08-01", 6, 15, false)).toBe("2027-06-15");
  });

  it("returns the same date when it exactly matches and strictlyAfter is false", () => {
    expect(nextAnnualOccurrence("2026-06-15", 6, 15, false)).toBe("2026-06-15");
  });

  it("rolls to next year when the date matches exactly and strictlyAfter is true", () => {
    expect(nextAnnualOccurrence("2026-06-15", 6, 15, true)).toBe("2027-06-15");
  });

  it("clamps Feb 29 to Feb 28 on a non-leap target year instead of throwing", () => {
    // 2026 is not a leap year -- this is the exact bug fixed this session.
    expect(nextAnnualOccurrence("2026-01-01", 2, 29, false)).toBe("2026-02-28");
  });

  it("resolves Feb 29 correctly on a leap target year", () => {
    expect(nextAnnualOccurrence("2028-01-01", 2, 29, false)).toBe("2028-02-29");
  });
});
