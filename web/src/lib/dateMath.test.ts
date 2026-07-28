import { describe, expect, it } from "vitest";
import { addDays, dateInRange, nextAnnualOccurrence, projectOccurrencesInRange, resolveInterval } from "./dateMath";

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
});

describe("resolveInterval", () => {
  it("returns the default interval when no override period matches", () => {
    expect(resolveInterval(7, [], "2026-07-01")).toBe(7);
  });

  it("returns an override period's interval when the date falls inside it", () => {
    const periods = [{ start_month: 12, start_day: 1, end_month: 2, end_day: 28, interval_days: 14 }];
    expect(resolveInterval(7, periods, "2026-01-15")).toBe(14);
    expect(resolveInterval(7, periods, "2026-07-15")).toBe(7);
  });
});

describe("nextAnnualOccurrence", () => {
  it("returns this year's date when it's still ahead (not strictlyAfter)", () => {
    expect(nextAnnualOccurrence("2026-01-01", 6, 15, false)).toBe("2026-06-15");
  });

  it("rolls to next year when this year's date has already passed", () => {
    expect(nextAnnualOccurrence("2026-08-01", 6, 15, false)).toBe("2027-06-15");
  });

  it("rolls to next year when the date matches exactly and strictlyAfter is true", () => {
    expect(nextAnnualOccurrence("2026-06-15", 6, 15, true)).toBe("2027-06-15");
  });

  it("clamps Feb 29 to Feb 28 on a non-leap target year instead of producing an invalid date", () => {
    expect(nextAnnualOccurrence("2026-01-01", 2, 29, false)).toBe("2026-02-28");
  });

  it("resolves Feb 29 correctly on a leap target year", () => {
    expect(nextAnnualOccurrence("2028-01-01", 2, 29, false)).toBe("2028-02-29");
  });
});

describe("projectOccurrencesInRange", () => {
  it("projects every daily occurrence within the window", () => {
    const results = projectOccurrencesInRange("2026-07-01", 1, [], "2026-07-01", "2026-07-05");
    expect(results).toEqual(["2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05"]);
  });

  it("stops projecting once the interval resolves to null (paused)", () => {
    const periods = [{ start_month: 1, start_day: 1, end_month: 12, end_day: 31, interval_days: null }];
    const results = projectOccurrencesInRange("2026-07-01", 7, periods, "2026-07-01", "2026-12-31");
    expect(results).toEqual(["2026-07-01"]);
  });

  it("excludes occurrences before rangeStart but includes the range boundaries", () => {
    const results = projectOccurrencesInRange("2026-06-01", 7, [], "2026-07-01", "2026-07-08");
    expect(results).toEqual(["2026-07-06"]);
  });

  it("projects annual occurrences a year apart when annualMonth/annualDay are set", () => {
    const results = projectOccurrencesInRange("2026-06-15", null, [], "2026-01-01", "2028-12-31", 500, 6, 15);
    expect(results).toEqual(["2026-06-15", "2027-06-15", "2028-06-15"]);
  });
});
