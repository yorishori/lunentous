import { useMemo, useRef, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { apiFetch } from "../api/client";
import type { OneTimeReminder, Plant, ReminderRule, ReminderState, ReminderType, PhaseWindow, TimelineEvent } from "../api/types";
import { buildCareTimeline, buildWeeks } from "../lib/careTimeline";
import CareTimelineGrid from "../components/CareTimelineGrid";
import MultiSelect from "../components/MultiSelect";
import Modal from "../components/Modal";
import TimelineEntryForm from "../components/TimelineEntryForm";
import { getIcon } from "../lib/icons";

// Not truly infinite/lazily-extended -- a large-but-bounded window instead
// (matches the Android app), which stays simple (no virtualization) and,
// for a personal plant-care app with a handful of plants, is effectively
// as far as anyone will ever scroll. Shifted a couple months into the past
// rather than starting exactly at "now": logged timeline entries are
// inherently backward-looking (you log what you did, dated today or
// earlier), so a window that only ever looked forward would never have
// anything to show in the week-detail panel outside the very first week.
const WINDOW_MONTHS_BACK = 2;
const WINDOW_MONTHS_FORWARD = 22;
const SCROLL_PAGE_WEEKS = 4;
const WEEK_WIDTH_PX = 37; // 2.3rem at the default 16px root -- close enough for a page-scroll amount

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
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
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const { windowStart, windowEnd, weeks } = useMemo(() => {
    const start = new Date();
    start.setDate(1);
    start.setHours(0, 0, 0, 0);
    start.setMonth(start.getMonth() - WINDOW_MONTHS_BACK);
    const end = new Date();
    end.setDate(1);
    end.setHours(0, 0, 0, 0);
    end.setMonth(end.getMonth() + WINDOW_MONTHS_FORWARD);
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
  const oneTimeRemindersQuery = useQuery({
    queryKey: ["one-time-reminders"],
    queryFn: () => apiFetch<OneTimeReminder[]>("/one-time-reminders"),
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
  const relevantOneTimeReminders = (oneTimeRemindersQuery.data ?? []).filter((r) => effectivePlantIds.has(r.plant_id));

  const { activities, ranges, events } = buildCareTimeline({
    plants: effectivePlants,
    reminderStates: relevantStates,
    rulesByPlantId,
    phaseWindowsByPlantId,
    oneTimeReminders: relevantOneTimeReminders,
    weeks,
    windowStart: isoDate(windowStart),
    windowEnd: isoDate(windowEnd),
  });
  const week = weeks[selectedWeek];
  const weekFrom = week?.startDate;
  const weekTo = week ? addDays(week.startDate, 6) : undefined;

  const timelineQueries = useQueries({
    queries: effectivePlants.map((p) => ({
      queryKey: ["timeline-week", p.id, weekFrom, weekTo],
      queryFn: () => apiFetch<TimelineEvent[]>(`/plants/${p.id}/timeline?from=${weekFrom}&to=${weekTo}&limit=100`),
      enabled: !!weekFrom && !!weekTo,
    })),
  });
  const reminderTypesById = new Map((reminderTypesQuery.data ?? []).map((t) => [t.id, t]));
  const weekEntries = effectivePlants
    .flatMap((p, idx) => (timelineQueries[idx]?.data ?? []).map((event) => ({ event, plant: p })))
    .sort((a, b) => b.event.event_date.localeCompare(a.event.event_date));

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.85rem", flexWrap: "wrap", gap: "0.75rem" }}>
        <h1 style={{ margin: 0 }}>Care timeline</h1>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", flexWrap: "wrap" }}>
          <MultiSelect
            options={allPlants.map((p) => ({ id: p.id, label: p.name }))}
            selected={selectedPlantIds}
            onChange={setSelectedPlantIds}
          />
          <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
            <Plus size={16} /> New entry
          </button>
          <div style={{ display: "flex", gap: "0.25rem" }}>
            <button
              type="button"
              className="btn secondary icon-btn"
              aria-label="Scroll to earlier weeks"
              onClick={() => scrollContainerRef.current?.scrollBy({ left: -WEEK_WIDTH_PX * SCROLL_PAGE_WEEKS, behavior: "smooth" })}
            >
              <ChevronLeft size={16} />
            </button>
            <button
              type="button"
              className="btn secondary icon-btn"
              aria-label="Scroll to later weeks"
              onClick={() => scrollContainerRef.current?.scrollBy({ left: WEEK_WIDTH_PX * SCROLL_PAGE_WEEKS, behavior: "smooth" })}
            >
              <ChevronRight size={16} />
            </button>
          </div>
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
        plants={effectivePlants}
        ranges={ranges}
        events={events}
        selectedWeek={selectedWeek}
        onSelectWeek={setSelectedWeek}
        scrollContainerRef={scrollContainerRef}
      />

      {week && (
        <div className="calendar-detail-panel">
          <h3 style={{ marginBottom: "0.75rem" }}>{formatWeekRange(week.startDate)}</h3>
          {weekEntries.length === 0 ? (
            <p style={{ color: "var(--text-muted)" }}>No entries logged this week.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
              {weekEntries.map(({ event, plant }) => (
                <EntryCard
                  key={event.id}
                  event={event}
                  plantName={plant.name}
                  reminderType={event.reminder_type_id != null ? reminderTypesById.get(event.reminder_type_id) : undefined}
                />
              ))}
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

// Untyped (journal-only) entries have no reminder type to represent, so
// they render with no icon at all rather than a placeholder.
function EntryCard({ event, plantName, reminderType }: { event: TimelineEvent; plantName: string; reminderType?: ReminderType }) {
  const Icon = reminderType?.icon ? getIcon(reminderType.icon) : null;
  const color = reminderType?.color ?? "var(--accent)";

  return (
    <div className="card" style={{ padding: "0.75rem" }}>
      <div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
        {reminderType && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              width: "1.75rem",
              height: "1.75rem",
              borderRadius: "999px",
              background: reminderType.color ? `${reminderType.color}29` : "var(--accent-soft)",
              color,
              flexShrink: 0,
            }}
          >
            {Icon && <Icon size={14} />}
          </span>
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600 }}>{plantName}</div>
          {reminderType && (
            <span style={{ fontSize: "0.78rem", color }}>{reminderType.name}</span>
          )}
        </div>
        <span style={{ fontSize: "0.78rem", color: "var(--text-muted)", whiteSpace: "nowrap" }}>{event.event_date}</span>
      </div>
      {event.text && <p style={{ marginBottom: 0, marginTop: "0.5rem" }}>{event.text}</p>}
      {event.photos.length > 0 && (
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.5rem" }}>
          {event.photos.map((p) => (
            <img
              key={p.id}
              src={`/photos/${p.file_path}`}
              alt=""
              style={{ width: 64, height: 64, objectFit: "cover", borderRadius: "8px" }}
            />
          ))}
        </div>
      )}
    </div>
  );
}
