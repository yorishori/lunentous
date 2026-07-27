import { useMemo, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { Plus, type LucideIcon } from "lucide-react";
import { apiFetch } from "../api/client";
import type { Plant, ReminderRule, ReminderState, ReminderType, PhaseWindow } from "../api/types";
import { buildCareTimeline, buildWeeks } from "../lib/careTimeline";
import CareTimelineGrid, { activityIcon } from "../components/CareTimelineGrid";
import MultiSelect from "../components/MultiSelect";
import Modal from "../components/Modal";
import TimelineEntryForm from "../components/TimelineEntryForm";

const WINDOW_MONTHS = 4;

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatWeekRange(startIso: string): string {
  const [y, m, d] = startIso.split("-").map(Number);
  const start = new Date(y, m - 1, d);
  const end = new Date(start);
  end.setDate(end.getDate() + 6);
  const fmt = (dt: Date) => dt.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  return `Week of ${fmt(start)} – ${fmt(end)}`;
}

/**
 * Multi-month care timeline: phase windows render as range pills (one lane
 * per phase type, merged across plants), reminder occurrences render as
 * point dots (one lane per reminder type, projected forward the same way
 * the old month grid's dashed markers were). Mirrors the Android app's
 * CareTimelineScreen.kt -- see lib/careTimeline.ts for the shared data
 * model both derive from the same server responses.
 *
 * Per-entry edit/delete moved to Plant Detail's own timeline feed, which
 * already has it -- this page only ever creates.
 */
export default function Calendar() {
  const [selectedPlantIds, setSelectedPlantIds] = useState<number[]>([]);
  const [createOpen, setCreateOpen] = useState(false);

  const { windowStart, windowEnd, weeks } = useMemo(() => {
    const start = new Date();
    start.setDate(1);
    start.setHours(0, 0, 0, 0);
    const end = new Date(start);
    end.setMonth(end.getMonth() + WINDOW_MONTHS);
    end.setDate(end.getDate() - 1);
    return { windowStart: start, windowEnd: end, weeks: buildWeeks(start, end) };
  }, []);

  // Lazy initializer runs once on mount, using `weeks` from the memo above
  // (itself computed once) -- lands on "today's week" without a separate
  // effect just to seed it.
  const [selectedWeek, setSelectedWeek] = useState(() => {
    const todayIso = isoDate(new Date());
    let idx = 0;
    for (const w of weeks) {
      if (w.startDate <= todayIso) idx = w.index;
    }
    return idx;
  });

  const plantsQuery = useQuery({
    queryKey: ["plants", { archived: false }],
    queryFn: () => apiFetch<Plant[]>("/plants?archived=false"),
  });
  const reminderTypesQuery = useQuery({
    queryKey: ["reminder-types", { archived: false }],
    queryFn: () => apiFetch<ReminderType[]>("/reminder-types?archived=false"),
  });
  const statesQuery = useQuery({
    queryKey: ["reminder-states"],
    queryFn: () => apiFetch<ReminderState[]>("/reminder-states"),
  });

  const allPlants = plantsQuery.data ?? [];
  const effectivePlants = selectedPlantIds.length > 0 ? allPlants.filter((p) => selectedPlantIds.includes(p.id)) : allPlants;

  const ruleQueries = useQueries({
    queries: effectivePlants.map((p) => ({
      queryKey: ["reminder-rules", p.id],
      queryFn: () => apiFetch<ReminderRule[]>(`/plants/${p.id}/reminder-rules`),
    })),
  });
  const windowQueries = useQueries({
    queries: effectivePlants.map((p) => ({
      queryKey: ["phase-windows", p.id],
      queryFn: () => apiFetch<PhaseWindow[]>(`/plants/${p.id}/phase-windows`),
    })),
  });

  const rulesByPlantId = new Map<number, ReminderRule[]>();
  const phaseWindowsByPlantId = new Map<number, PhaseWindow[]>();
  effectivePlants.forEach((p, idx) => {
    rulesByPlantId.set(p.id, ruleQueries[idx]?.data ?? []);
    phaseWindowsByPlantId.set(p.id, windowQueries[idx]?.data ?? []);
  });

  const effectivePlantIds = new Set(effectivePlants.map((p) => p.id));
  const relevantStates = (statesQuery.data ?? []).filter((s) => effectivePlantIds.has(s.plant_id));

  const { activities, ranges, events } = buildCareTimeline({
    plants: effectivePlants,
    reminderStates: relevantStates,
    rulesByPlantId,
    phaseWindowsByPlantId,
    weeks,
    windowStart: isoDate(windowStart),
    windowEnd: isoDate(windowEnd),
  });

  const week = weeks[selectedWeek];
  const activitiesById = new Map(activities.map((a) => [a.id, a]));
  const activeRanges = ranges.filter((r) => selectedWeek >= r.startWeek && selectedWeek <= r.endWeek);
  const activeEvents = events.filter((e) => e.week === selectedWeek);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.85rem", flexWrap: "wrap", gap: "0.75rem" }}>
        <h1 style={{ margin: 0 }}>Calendar</h1>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", flexWrap: "wrap" }}>
          <MultiSelect
            options={allPlants.map((p) => ({ id: p.id, label: p.name }))}
            selected={selectedPlantIds}
            onChange={setSelectedPlantIds}
          />
          <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
            <Plus size={16} /> New entry
          </button>
        </div>
      </div>

      <p style={{ color: "var(--text-muted)", marginTop: 0 }}>
        {weeks.length > 0 && `${weeks[0].startDate} through ${weeks[weeks.length - 1].startDate} — `}
        tap a week to see what's active. Solid pills are active phase windows; dots are reminder occurrences (due or
        projected).
      </p>

      <CareTimelineGrid
        weeks={weeks}
        activities={activities}
        ranges={ranges}
        events={events}
        selectedWeek={selectedWeek}
        onSelectWeek={setSelectedWeek}
      />

      {week && (
        <div className="calendar-detail-panel">
          <h3 style={{ marginBottom: "0.75rem" }}>{formatWeekRange(week.startDate)}</h3>
          {activeRanges.length === 0 && activeEvents.length === 0 ? (
            <p style={{ color: "var(--text-muted)" }}>Routine care only — nothing scheduled this week.</p>
          ) : (
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              {activeRanges.map((range, i) => {
                const activity = activitiesById.get(range.activityId);
                if (!activity) return null;
                return <DetailChip key={`r-${i}`} icon={activityIcon(activity)} label={activity.label} plantName={range.plantName} color={activity.color} />;
              })}
              {activeEvents.map((event, i) => {
                const activity = activitiesById.get(event.activityId);
                if (!activity) return null;
                return <DetailChip key={`e-${i}`} icon={activityIcon(activity)} label={activity.label} plantName={event.plantName} color={activity.color} />;
              })}
            </div>
          )}
        </div>
      )}

      <Modal open={createOpen} title="New timeline entry" onClose={() => setCreateOpen(false)}>
        <TimelineEntryForm plants={allPlants} reminderTypes={reminderTypesQuery.data ?? []} onDone={() => setCreateOpen(false)} />
      </Modal>
    </div>
  );
}

function DetailChip({ icon: Icon, label, plantName, color }: { icon: LucideIcon; label: string; plantName: string; color: string }) {
  return (
    <span
      className="badge"
      style={{ display: "inline-flex", alignItems: "center", gap: "0.35rem", background: `${color}29`, color }}
    >
      <Icon size={14} />
      {label} · {plantName}
    </span>
  );
}
