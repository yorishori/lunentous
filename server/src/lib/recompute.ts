import { db } from "../db/client.js";
import { addDays, nextAnnualOccurrence, resolveInterval, type ISODate, type OverridePeriodLike } from "./dates.js";

interface ReminderRuleRow {
  id: number;
  plant_id: number;
  reminder_type_id: number;
  default_interval_days: number | null;
  annual_month: number | null;
  annual_day: number | null;
  created_at: string;
}

interface LatestEventRow {
  event_date: string;
}

/**
 * spec §4.2 recompute_reminder_state. Always recalculates from scratch off
 * the most recent qualifying timeline event, so edits/deletes recompute
 * correctly rather than only forward-adjusting from the prior state.
 */
export function recomputeReminderState(plantId: number, reminderTypeId: number): void {
  const rule = db
    .prepare("SELECT * FROM reminder_rules WHERE plant_id = ? AND reminder_type_id = ?")
    .get(plantId, reminderTypeId) as ReminderRuleRow | undefined;

  if (!rule) {
    db.prepare("DELETE FROM reminder_states WHERE plant_id = ? AND reminder_type_id = ?").run(
      plantId,
      reminderTypeId
    );
    return;
  }

  const latestEvent = db
    .prepare(
      `SELECT event_date FROM timeline_events
       WHERE plant_id = ? AND reminder_type_id = ?
       ORDER BY event_date DESC, id DESC LIMIT 1`
    )
    .get(plantId, reminderTypeId) as LatestEventRow | undefined;

  // Never logged: baseline is the rule's creation date, so a new rule has an
  // immediate first due date instead of sitting undefined (spec §4.2, §10).
  const baselineDate: ISODate = latestEvent ? latestEvent.event_date : rule.created_at.slice(0, 10);

  const periods = db
    .prepare(
      "SELECT start_month, start_day, end_month, end_day, interval_days FROM override_periods WHERE reminder_rule_id = ?"
    )
    .all(rule.id) as OverridePeriodLike[];

  const interval = resolveInterval({ default_interval_days: rule.default_interval_days }, periods, baselineDate);
  // Never logged: the baseline date itself is due (today counts as day
  // one of the interval), not baseline + interval -- otherwise a fresh
  // "every N days" rule wouldn't come due until N days from now instead
  // of starting its count today.
  const dueDate =
    interval != null
      ? latestEvent
        ? addDays(baselineDate, interval)
        : baselineDate
      : rule.annual_month != null && rule.annual_day != null
        ? nextAnnualOccurrence(baselineDate, rule.annual_month, rule.annual_day, latestEvent != null)
        : null;

  db.prepare(
    `INSERT INTO reminder_states (plant_id, reminder_type_id, due_date, notified)
     VALUES (?, ?, ?, 0)
     ON CONFLICT(plant_id, reminder_type_id)
     DO UPDATE SET due_date = excluded.due_date, notified = 0`
  ).run(plantId, reminderTypeId, dueDate);
}
