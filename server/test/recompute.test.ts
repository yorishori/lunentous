import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db } from "../src/db/client.js";
import { migrate } from "../src/db/migrate.js";
import { recomputeReminderState } from "../src/lib/recompute.js";
import { todayLocalDate } from "../src/lib/dates.js";
import { resetDb } from "./helpers.js";

beforeAll(() => {
  migrate();
});

beforeEach(() => {
  resetDb();
});

function insertPlant(name = "Test Plant"): number {
  return Number(db.prepare("INSERT INTO plants (name) VALUES (?)").run(name).lastInsertRowid);
}

function insertReminderType(name = "Watering"): number {
  return Number(db.prepare("INSERT INTO reminder_types (name) VALUES (?)").run(name).lastInsertRowid);
}

function getDueDate(plantId: number, reminderTypeId: number): string | null {
  const row = db
    .prepare("SELECT due_date FROM reminder_states WHERE plant_id = ? AND reminder_type_id = ?")
    .get(plantId, reminderTypeId) as { due_date: string | null } | undefined;
  return row?.due_date ?? null;
}

describe("recomputeReminderState", () => {
  it("deletes the reminder_state when the rule no longer exists", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare("INSERT INTO reminder_states (plant_id, reminder_type_id, due_date) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      "2026-01-01"
    );
    recomputeReminderState(plantId, typeId);
    expect(getDueDate(plantId, typeId)).toBeNull();
  });

  it("a brand-new never-logged interval rule is due today, not N days from now", () => {
    // This is the exact off-by-one bug fixed this session: a fresh "every
    // N days" rule used to be due baseline+N instead of counting today as
    // day one.
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      1
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBe(todayLocalDate());
  });

  it("a never-logged rule with a longer interval is still due today (day one of the count)", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      19
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBe(todayLocalDate());
  });

  it("after a logged completion, the next due date is baseline + interval", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      7
    );
    db.prepare("INSERT INTO timeline_events (plant_id, reminder_type_id, event_date) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      "2026-01-01"
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBe("2026-01-08");
  });

  it("a rule with no default interval and no matching override is paused (null due_date)", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      null
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBeNull();
  });

  it("an annual fixed-date rule with no prior log is due on this year's occurrence", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare(
      "INSERT INTO reminder_rules (plant_id, reminder_type_id, annual_month, annual_day) VALUES (?, ?, ?, ?)"
    ).run(plantId, typeId, 12, 25);

    recomputeReminderState(plantId, typeId);

    const dueDate = getDueDate(plantId, typeId);
    expect(dueDate).not.toBeNull();
    expect(dueDate!.slice(5)).toBe("12-25");
  });

  it("an annual rule already logged this year rolls to next year, not the same date again", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    db.prepare(
      "INSERT INTO reminder_rules (plant_id, reminder_type_id, annual_month, annual_day) VALUES (?, ?, ?, ?)"
    ).run(plantId, typeId, 6, 15);
    db.prepare("INSERT INTO timeline_events (plant_id, reminder_type_id, event_date) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      "2026-06-15"
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBe("2027-06-15");
  });

  it("an override period active on the baseline date takes precedence over the default interval", () => {
    const plantId = insertPlant();
    const typeId = insertReminderType();
    const ruleId = Number(
      db
        .prepare("INSERT INTO reminder_rules (plant_id, reminder_type_id, default_interval_days) VALUES (?, ?, ?)")
        .run(plantId, typeId, 7).lastInsertRowid
    );
    db.prepare(
      "INSERT INTO override_periods (reminder_rule_id, start_month, start_day, end_month, end_day, interval_days) VALUES (?, 1, 1, 12, 31, ?)"
    ).run(ruleId, 30);
    db.prepare("INSERT INTO timeline_events (plant_id, reminder_type_id, event_date) VALUES (?, ?, ?)").run(
      plantId,
      typeId,
      "2026-01-01"
    );

    recomputeReminderState(plantId, typeId);

    expect(getDueDate(plantId, typeId)).toBe("2026-01-31");
  });
});
