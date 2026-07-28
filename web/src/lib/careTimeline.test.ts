import { describe, expect, it } from "vitest";
import { buildCareTimeline, buildWeeks } from "./careTimeline";
import type { OneTimeReminder, Plant, PhaseWindow, ReminderRule, ReminderState } from "../api/types";

function makePlant(id: number, name = "Test Plant"): Plant {
  return {
    id,
    name,
    species: null,
    location: null,
    acquired_date: null,
    avatar_photo_id: null,
    avatar_photo_path: null,
    general_notes: null,
    archived: 0,
    created_at: "2026-01-01",
    updated_at: "2026-01-01",
  };
}

describe("buildWeeks", () => {
  it("produces 7-day blocks covering the window", () => {
    const weeks = buildWeeks(new Date(2026, 5, 1), new Date(2026, 5, 21));
    expect(weeks.map((w) => w.startDate)).toEqual(["2026-06-01", "2026-06-08", "2026-06-15"]);
    expect(weeks.map((w) => w.index)).toEqual([0, 1, 2]);
  });

  it("labels only the first week of each new month", () => {
    const weeks = buildWeeks(new Date(2026, 5, 25), new Date(2026, 6, 10));
    const labeled = weeks.filter((w) => w.monthLabel !== null);
    expect(labeled.length).toBeGreaterThan(0);
    expect(labeled[0].monthLabel).toBe("Jun");
  });
});

describe("buildCareTimeline", () => {
  it("builds a phase-window range spanning the weeks it's active", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 2, 1));
    const phaseWindow: PhaseWindow = {
      id: 1,
      plant_id: 1,
      phase_type_id: 10,
      start_month: 1,
      start_day: 1,
      end_month: 1,
      end_day: 31,
      notes: null,
      phase_type_name: "Dormancy",
      phase_type_color: "#89b4fa",
    };

    const { activities, ranges } = buildCareTimeline({
      plants: [plant],
      reminderStates: [],
      rulesByPlantId: new Map(),
      phaseWindowsByPlantId: new Map([[1, [phaseWindow]]]),
      oneTimeReminders: [],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-03-01",
    });

    expect(ranges).toHaveLength(1);
    expect(ranges[0].plantId).toBe(1);
    expect(ranges[0].startWeek).toBe(0);
    expect(activities.find((a) => a.id === "phase-10")?.label).toBe("Dormancy");
  });

  it("projects a reminder occurrence as a point event", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 1, 1));
    const state: ReminderState = {
      id: 1,
      plant_id: 1,
      reminder_type_id: 5,
      due_date: "2026-01-08",
      notified: 0,
      days_overdue: null,
      reminder_type_name: "Watering",
      reminder_type_icon: "Droplet",
      reminder_type_color: "#89b4fa",
    };

    const { activities, events } = buildCareTimeline({
      plants: [plant],
      reminderStates: [state],
      rulesByPlantId: new Map(),
      phaseWindowsByPlantId: new Map(),
      oneTimeReminders: [],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-02-01",
    });

    expect(events).toHaveLength(1);
    expect(events[0].date).toBe("2026-01-08");
    expect(activities.find((a) => a.id === "reminder-5")?.label).toBe("Watering");
  });

  it("projects every interval occurrence within the window when a rule is present", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 1, 1));
    const state: ReminderState = {
      id: 1,
      plant_id: 1,
      reminder_type_id: 5,
      due_date: "2026-01-01",
      notified: 0,
      days_overdue: null,
    };
    const rule: ReminderRule = {
      id: 1,
      plant_id: 1,
      reminder_type_id: 5,
      default_interval_days: 7,
      annual_month: null,
      annual_day: null,
      override_periods: [],
    };

    const { events } = buildCareTimeline({
      plants: [plant],
      reminderStates: [state],
      rulesByPlantId: new Map([[1, [rule]]]),
      phaseWindowsByPlantId: new Map(),
      oneTimeReminders: [],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-01-22",
    });

    expect(events.map((e) => e.date)).toEqual(["2026-01-01", "2026-01-08", "2026-01-15", "2026-01-22"]);
  });

  it("includes an uncompleted one-time reminder as a point event under the shared synthetic activity", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 1, 1));
    const reminder: OneTimeReminder = {
      id: 1,
      plant_id: 1,
      due_date: "2026-01-15",
      text: "Give this plant to a friend",
      completed_at: null,
      created_at: "2026-01-01",
    };

    const { activities, events } = buildCareTimeline({
      plants: [plant],
      reminderStates: [],
      rulesByPlantId: new Map(),
      phaseWindowsByPlantId: new Map(),
      oneTimeReminders: [reminder],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-02-01",
    });

    expect(events).toHaveLength(1);
    expect(events[0].activityId).toBe("one-time-reminder");
    expect(activities.find((a) => a.id === "one-time-reminder")).toBeTruthy();
  });

  it("excludes a completed one-time reminder", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 1, 1));
    const reminder: OneTimeReminder = {
      id: 1,
      plant_id: 1,
      due_date: "2026-01-15",
      text: "Buy a new pot",
      completed_at: "2026-01-10T00:00:00.000Z",
      created_at: "2026-01-01",
    };

    const { events } = buildCareTimeline({
      plants: [plant],
      reminderStates: [],
      rulesByPlantId: new Map(),
      phaseWindowsByPlantId: new Map(),
      oneTimeReminders: [reminder],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-02-01",
    });

    expect(events).toHaveLength(0);
  });

  it("excludes a one-time reminder outside the window", () => {
    const plant = makePlant(1);
    const weeks = buildWeeks(new Date(2026, 0, 1), new Date(2026, 1, 1));
    const reminder: OneTimeReminder = {
      id: 1,
      plant_id: 1,
      due_date: "2027-06-01",
      text: "Far in the future",
      completed_at: null,
      created_at: "2026-01-01",
    };

    const { events } = buildCareTimeline({
      plants: [plant],
      reminderStates: [],
      rulesByPlantId: new Map(),
      phaseWindowsByPlantId: new Map(),
      oneTimeReminders: [reminder],
      weeks,
      windowStart: "2026-01-01",
      windowEnd: "2026-02-01",
    });

    expect(events).toHaveLength(0);
  });
});
