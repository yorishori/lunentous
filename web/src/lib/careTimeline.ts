// Multi-month plant care timeline: range activities (phase windows) as
// pill lanes, point activities (reminder occurrences) as dot lanes. Pure
// data-building logic, no React -- mirrors the Android app's
// CareTimelineViewModel (ui/calendar/timeline/) so both platforms compute
// the same thing from the same server data.
import type { Plant, PhaseWindow, ReminderRule, ReminderState } from "../api/types";
import { dateInRange, projectOccurrencesInRange, type ISODate } from "./dateMath";

export type ActivityKind = "range" | "point";

export interface CareActivity {
  id: string;
  label: string;
  kind: ActivityKind;
  color: string;
  icon: string | null; // lucide icon name (point activities only); null falls back to a generic icon
}

export interface SeasonRange {
  activityId: string;
  plantId: number;
  plantName: string;
  startWeek: number;
  endWeek: number;
}

export interface CareEvent {
  activityId: string;
  plantId: number;
  plantName: string;
  week: number;
  date: ISODate;
}

export interface WeekInfo {
  index: number;
  startDate: ISODate;
  monthLabel: string | null;
}

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function isoDate(d: Date): ISODate {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Weeks are plain 7-day blocks from `windowStart`, not calendar (Mon-Sun)
 * weeks -- there's no requirement they align to a particular start-of-week
 * convention, only that every lane uses the same blocks. */
export function buildWeeks(windowStart: Date, windowEndInclusive: Date): WeekInfo[] {
  const weeks: WeekInfo[] = [];
  const cursor = new Date(windowStart);
  let index = 0;
  let lastMonth = -1;
  while (cursor <= windowEndInclusive) {
    const monthLabel = cursor.getMonth() !== lastMonth ? MONTH_LABELS[cursor.getMonth()] : null;
    weeks.push({ index, startDate: isoDate(cursor), monthLabel });
    lastMonth = cursor.getMonth();
    cursor.setDate(cursor.getDate() + 7);
    index++;
  }
  return weeks;
}

function weekIndexForIsoDate(dateIso: ISODate, weeks: WeekInfo[]): number {
  for (let i = weeks.length - 1; i >= 0; i--) {
    if (dateIso >= weeks[i].startDate) return weeks[i].index;
  }
  return 0;
}

export interface CareTimelineInput {
  plants: Plant[];
  reminderStates: ReminderState[]; // must be the /reminder-states response (plant_name/reminder_type_* pre-joined)
  rulesByPlantId: Map<number, ReminderRule[]>;
  phaseWindowsByPlantId: Map<number, PhaseWindow[]>;
  weeks: WeekInfo[];
  windowStart: ISODate;
  windowEnd: ISODate;
}

export interface CareTimelineData {
  activities: CareActivity[];
  ranges: SeasonRange[];
  events: CareEvent[];
}

/** Colors come from each reminder/phase type's own already-existing
 * user-chosen color (used everywhere else in the app), not a fixed
 * 3-role palette -- every type already has one, so there's no palette
 * left to invent. */
export function buildCareTimeline(input: CareTimelineInput): CareTimelineData {
  const { plants, reminderStates, rulesByPlantId, phaseWindowsByPlantId, weeks, windowStart, windowEnd } = input;
  const plantsById = new Map(plants.map((p) => [p.id, p]));
  const lastWeek = weeks[weeks.length - 1];

  const ranges: SeasonRange[] = [];
  const rangeActivitiesById = new Map<string, CareActivity>();
  for (const plant of plants) {
    for (const w of phaseWindowsByPlantId.get(plant.id) ?? []) {
      const activityId = `phase-${w.phase_type_id}`;
      if (!rangeActivitiesById.has(activityId)) {
        rangeActivitiesById.set(activityId, {
          id: activityId,
          label: w.phase_type_name ?? "Phase",
          kind: "range",
          color: w.phase_type_color ?? "#8839ef",
          icon: null,
        });
      }
      let segmentStart: number | null = null;
      for (const week of weeks) {
        const active = dateInRange(week.startDate, w.start_month, w.start_day, w.end_month, w.end_day);
        if (active && segmentStart === null) segmentStart = week.index;
        if (!active && segmentStart !== null) {
          ranges.push({ activityId, plantId: plant.id, plantName: plant.name, startWeek: segmentStart, endWeek: week.index - 1 });
          segmentStart = null;
        }
      }
      if (segmentStart !== null && lastWeek) {
        ranges.push({ activityId, plantId: plant.id, plantName: plant.name, startWeek: segmentStart, endWeek: lastWeek.index });
      }
    }
  }

  const events: CareEvent[] = [];
  const pointActivitiesById = new Map<string, CareActivity>();
  for (const state of reminderStates) {
    if (!state.due_date) continue;
    const plant = plantsById.get(state.plant_id);
    if (!plant) continue;
    const activityId = `reminder-${state.reminder_type_id}`;
    if (!pointActivitiesById.has(activityId)) {
      pointActivitiesById.set(activityId, {
        id: activityId,
        label: state.reminder_type_name ?? "Reminder",
        kind: "point",
        color: state.reminder_type_color ?? "#8839ef",
        icon: state.reminder_type_icon ?? null,
      });
    }
    const rule = (rulesByPlantId.get(state.plant_id) ?? []).find((r) => r.reminder_type_id === state.reminder_type_id);
    const occurrences = rule
      ? projectOccurrencesInRange(state.due_date, rule.default_interval_days, rule.override_periods, windowStart, windowEnd)
      : state.due_date >= windowStart && state.due_date <= windowEnd
        ? [state.due_date]
        : [];
    for (const dateStr of occurrences) {
      events.push({ activityId, plantId: plant.id, plantName: plant.name, week: weekIndexForIsoDate(dateStr, weeks), date: dateStr });
    }
  }

  const activities = [
    ...[...rangeActivitiesById.values()].filter((a) => ranges.some((r) => r.activityId === a.id)),
    ...[...pointActivitiesById.values()].filter((a) => events.some((e) => e.activityId === a.id)),
  ];

  return { activities, ranges, events };
}
