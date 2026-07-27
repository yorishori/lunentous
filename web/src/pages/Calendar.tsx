import { useState } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { Plant, ReminderRule, ReminderState, ReminderType, PhaseWindow, TimelineEvent } from "../api/types";
import { projectOccurrencesInRange, dateInRange } from "../lib/dateMath";
import CalendarGrid, { type CalendarMarker, type PhaseBand } from "../components/CalendarGrid";
import MultiSelect from "../components/MultiSelect";
import Modal from "../components/Modal";
import ConfirmDialog from "../components/ConfirmDialog";
import TimelineEntryForm from "../components/TimelineEntryForm";
import TypeBadge from "../components/TypeBadge";
import { useToast } from "../components/Toast";
import { getIcon } from "../lib/icons";

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

interface GlobalReminderState extends ReminderState {
  plant_name: string;
}

type DayDetailItem =
  | { kind: "due" | "projected"; plantName: string; reminderTypeName: string; color?: string; icon?: string | null }
  | { kind: "logged"; plantName: string; event: TimelineEvent; typeName?: string; color?: string | null };

export default function Calendar() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);
  const [selectedPlantIds, setSelectedPlantIds] = useState<number[]>([]);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<TimelineEvent | null>(null);
  const [deletingEvent, setDeletingEvent] = useState<TimelineEvent | null>(null);

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
    queryFn: () => apiFetch<GlobalReminderState[]>("/reminder-states"),
  });

  const allPlants = plantsQuery.data ?? [];
  const effectivePlants =
    selectedPlantIds.length > 0 ? allPlants.filter((p) => selectedPlantIds.includes(p.id)) : allPlants;

  const daysInMonth = new Date(viewYear, viewMonth, 0).getDate();
  const monthStart = `${viewYear}-${pad(viewMonth)}-01`;
  const monthEnd = `${viewYear}-${pad(viewMonth)}-${pad(daysInMonth)}`;

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

  const timelineQueries = useQueries({
    queries: effectivePlants.map((p) => ({
      queryKey: ["timeline", p.id, "calendar", viewYear, viewMonth],
      queryFn: () =>
        apiFetch<TimelineEvent[]>(`/plants/${p.id}/timeline?from=${monthStart}&to=${monthEnd}&limit=200`),
    })),
  });

  const rulesByPlantAndType = new Map<string, ReminderRule>();
  effectivePlants.forEach((p, idx) => {
    for (const rule of ruleQueries[idx]?.data ?? []) {
      rulesByPlantAndType.set(`${p.id}:${rule.reminder_type_id}`, rule);
    }
  });

  const markersByDate = new Map<string, CalendarMarker[]>();
  const phaseBandsByDate = new Map<string, PhaseBand[]>();
  const dayDetailsByDate = new Map<string, DayDetailItem[]>();

  function addMarker(date: string, marker: CalendarMarker) {
    const list = markersByDate.get(date) ?? [];
    list.push(marker);
    markersByDate.set(date, list);
  }

  function addPhaseBand(date: string, band: PhaseBand) {
    const list = phaseBandsByDate.get(date) ?? [];
    list.push(band);
    phaseBandsByDate.set(date, list);
  }

  function addDayDetail(date: string, item: DayDetailItem) {
    const list = dayDetailsByDate.get(date) ?? [];
    list.push(item);
    dayDetailsByDate.set(date, list);
  }

  // Reminder due dates + projected future occurrences (spec §6)
  const effectivePlantIds = new Set(effectivePlants.map((p) => p.id));
  for (const state of statesQuery.data ?? []) {
    if (!state.due_date || !effectivePlantIds.has(state.plant_id)) continue;
    const rule = rulesByPlantAndType.get(`${state.plant_id}:${state.reminder_type_id}`);
    const occurrences = rule
      ? projectOccurrencesInRange(state.due_date, rule.default_interval_days, rule.override_periods, monthStart, monthEnd)
      : state.due_date >= monthStart && state.due_date <= monthEnd
        ? [state.due_date]
        : [];

    for (const date of occurrences) {
      const isActualDue = date === state.due_date;
      const kind = isActualDue ? "due" : "projected";
      addMarker(date, {
        key: `state-${state.id}-${date}`,
        label: `${state.plant_name}: ${state.reminder_type_name}`,
        kind,
        color: state.reminder_type_color,
      });
      addDayDetail(date, {
        kind,
        plantName: state.plant_name,
        reminderTypeName: state.reminder_type_name ?? "",
        color: state.reminder_type_color,
        icon: state.reminder_type_icon,
      });
    }
  }

  // Phase windows, shaded across their active date range
  effectivePlants.forEach((p, idx) => {
    const windows = windowQueries[idx]?.data ?? [];
    for (let d = 1; d <= daysInMonth; d++) {
      const iso = `${viewYear}-${pad(viewMonth)}-${pad(d)}`;
      for (const w of windows) {
        if (dateInRange(iso, w.start_month, w.start_day, w.end_month, w.end_day)) {
          addPhaseBand(iso, {
            color: w.phase_type_color ?? null,
            label: `${p.name}: ${w.phase_type_name}`,
          });
        }
      }
    }
  });

  // Logged timeline entries -- every entry, not just reminder completions
  effectivePlants.forEach((p, idx) => {
    const events = timelineQueries[idx]?.data ?? [];
    for (const event of events) {
      const type = reminderTypesQuery.data?.find((t) => t.id === event.reminder_type_id);
      addMarker(event.event_date, {
        key: `event-${event.id}`,
        label: `${p.name}: ${type?.name ?? "Note"}`,
        kind: "logged",
        color: type?.color ?? undefined,
      });
      addDayDetail(event.event_date, {
        kind: "logged",
        plantName: p.name,
        event,
        typeName: type?.name,
        color: type?.color,
      });
    }
  });

  const deleteEvent = useMutation({
    mutationFn: (id: number) => apiFetch(`/timeline/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["timeline"] });
      queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
      showToast("Timeline entry deleted", "success");
      setDeletingEvent(null);
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to delete entry", "error"),
  });

  function prevMonth() {
    if (viewMonth === 1) {
      setViewYear((y) => y - 1);
      setViewMonth(12);
    } else {
      setViewMonth((m) => m - 1);
    }
    setSelectedDay(null);
  }

  function nextMonth() {
    if (viewMonth === 12) {
      setViewYear((y) => y + 1);
      setViewMonth(1);
    } else {
      setViewMonth((m) => m + 1);
    }
    setSelectedDay(null);
  }

  function formatLongDate(iso: string): string {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d).toLocaleDateString(undefined, {
      weekday: "long",
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  }

  const selectedPhases = selectedDay ? (phaseBandsByDate.get(selectedDay) ?? []) : [];
  const selectedEntries = selectedDay ? (dayDetailsByDate.get(selectedDay) ?? []) : [];

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

      <div style={{ display: "flex", justifyContent: "center", gap: "0.75rem", alignItems: "center", marginBottom: "0.5rem" }}>
        <button type="button" className="btn secondary" onClick={prevMonth}>
          ←
        </button>
        <strong>{new Date(viewYear, viewMonth - 1, 1).toLocaleString(undefined, { month: "long", year: "numeric" })}</strong>
        <button type="button" className="btn secondary" onClick={nextMonth}>
          →
        </button>
      </div>

      <p style={{ color: "var(--text-muted)", marginTop: 0, textAlign: "center" }}>
        Solid markers are scheduled due dates, dashed are projected future occurrences, and bordered markers are
        logged entries. Click a day to see its phases and entries below.
      </p>

      <CalendarGrid
        year={viewYear}
        month={viewMonth}
        markersByDate={markersByDate}
        phaseBandsByDate={phaseBandsByDate}
        selectedDay={selectedDay}
        onDayClick={(date) => setSelectedDay((prev) => (prev === date ? null : date))}
      />

      {selectedDay && (
        <div className="calendar-detail-panel">
          <h3 style={{ marginBottom: "0.75rem" }}>{formatLongDate(selectedDay)}</h3>

          {selectedPhases.length > 0 && (
            <div style={{ marginBottom: "1.1rem" }}>
              <div style={{ fontSize: "0.8rem", color: "var(--text-muted)", marginBottom: "0.4rem" }}>Active phases</div>
              <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
                {selectedPhases.map((b, i) => (
                  <TypeBadge key={i} name={b.label} color={b.color} />
                ))}
              </div>
            </div>
          )}

          <div style={{ fontSize: "0.8rem", color: "var(--text-muted)", marginBottom: "0.5rem" }}>Entries</div>
          {selectedEntries.length === 0 && <p style={{ color: "var(--text-muted)" }}>Nothing for this day.</p>}
          <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
            {selectedEntries.map((item, i) =>
              item.kind === "logged" ? (
                <div key={i} className="card">
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem" }}>
                    <div>
                      <strong>{item.plantName}</strong>
                      {item.typeName && (
                        <span style={{ marginLeft: "0.5rem", display: "inline-block" }}>
                          <TypeBadge name={item.typeName} color={item.color} />
                        </span>
                      )}
                    </div>
                    <div style={{ display: "flex", gap: "0.4rem", flexShrink: 0 }}>
                      <button
                        type="button"
                        className="btn icon-btn secondary icon-btn-edit"
                        onClick={() => setEditingEvent(item.event)}
                        aria-label="Edit entry"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        type="button"
                        className="btn icon-btn secondary icon-btn-delete"
                        onClick={() => setDeletingEvent(item.event)}
                        aria-label="Delete entry"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                  {item.event.text && <p style={{ marginBottom: 0, marginTop: "0.5rem" }}>{item.event.text}</p>}
                  {item.event.photos.length > 0 && (
                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.6rem" }}>
                      {item.event.photos.map((p) => (
                        <img
                          key={p.id}
                          src={`/photos/${p.file_path}`}
                          alt=""
                          style={{ width: 80, height: 80, objectFit: "cover", borderRadius: "8px" }}
                        />
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                <div key={i} className="item-row" style={{ cursor: "default" }}>
                  <div className="item-row-main">
                    <span
                      style={{
                        display: "inline-flex",
                        width: "2rem",
                        height: "2rem",
                        borderRadius: "999px",
                        background: item.color ? `${item.color}29` : "var(--accent-soft)",
                        color: item.color ?? "var(--accent)",
                        alignItems: "center",
                        justifyContent: "center",
                        flexShrink: 0,
                      }}
                    >
                      {(() => {
                        const Icon = getIcon(item.icon);
                        return Icon ? <Icon size={16} /> : null;
                      })()}
                    </span>
                    <div>
                      <div style={{ fontWeight: 600 }}>
                        {item.plantName} — {item.reminderTypeName}
                      </div>
                      <div style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                        {item.kind === "due" ? "Scheduled" : "Projected (assumes on-time completion)"}
                      </div>
                    </div>
                  </div>
                </div>
              )
            )}
          </div>
        </div>
      )}

      <Modal open={createOpen} title="New timeline entry" onClose={() => setCreateOpen(false)}>
        <TimelineEntryForm
          plants={allPlants}
          reminderTypes={reminderTypesQuery.data ?? []}
          initialDate={selectedDay ?? undefined}
          onDone={() => setCreateOpen(false)}
        />
      </Modal>

      <Modal open={editingEvent !== null} title="Edit timeline entry" onClose={() => setEditingEvent(null)}>
        {editingEvent && (
          <TimelineEntryForm
            reminderTypes={reminderTypesQuery.data ?? []}
            existingEvent={editingEvent}
            onDone={() => setEditingEvent(null)}
          />
        )}
      </Modal>

      <ConfirmDialog
        open={deletingEvent !== null}
        title="Delete timeline entry?"
        message="This permanently removes this entry and its photos, and recalculates the reminder if it was tagged."
        confirmLabel="Delete"
        danger
        pending={deleteEvent.isPending}
        onConfirm={() => deletingEvent && deleteEvent.mutate(deletingEvent.id)}
        onCancel={() => setDeletingEvent(null)}
      />
    </div>
  );
}
