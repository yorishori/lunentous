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
import { useToast } from "../components/Toast";

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

interface GlobalReminderState extends ReminderState {
  plant_name: string;
}

type SelectedItem =
  | { kind: "due" | "projected"; plantName: string; reminderTypeName: string; date: string }
  | { kind: "logged"; plantName: string; event: TimelineEvent };

export default function Calendar() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);
  const [selectedPlantIds, setSelectedPlantIds] = useState<number[]>([]);
  const [selected, setSelected] = useState<SelectedItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createDate, setCreateDate] = useState<string | undefined>(undefined);
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
      addMarker(date, {
        key: `state-${state.id}-${date}`,
        label: `${state.plant_name}: ${state.reminder_type_name}`,
        kind: isActualDue ? "due" : "projected",
        color: state.reminder_type_color,
        onClick: () =>
          setSelected({
            kind: isActualDue ? "due" : "projected",
            plantName: state.plant_name,
            reminderTypeName: state.reminder_type_name ?? "",
            date,
          }),
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
            color: w.phase_type_color ?? "var(--accent)",
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
        onClick: () => setSelected({ kind: "logged", plantName: p.name, event }),
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
      setSelected(null);
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
    setSelected(null);
  }

  function nextMonth() {
    if (viewMonth === 12) {
      setViewYear((y) => y + 1);
      setViewMonth(1);
    } else {
      setViewMonth((m) => m + 1);
    }
    setSelected(null);
  }

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
          <button
            type="button"
            className="btn"
            onClick={() => {
              setCreateDate(undefined);
              setCreateOpen(true);
            }}
          >
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
        logged entries. Click a day to add an entry, or click a marker for details.
      </p>

      <CalendarGrid
        year={viewYear}
        month={viewMonth}
        markersByDate={markersByDate}
        phaseBandsByDate={phaseBandsByDate}
        onDayClick={(date) => {
          setCreateDate(date);
          setCreateOpen(true);
        }}
      />

      {selected && (
        <div className="calendar-detail-panel card">
          {selected.kind === "logged" ? (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem" }}>
                <div>
                  <strong>{selected.plantName}</strong> · {selected.event.event_date}
                  {selected.event.reminder_type_id != null && (
                    <span className="badge ok" style={{ marginLeft: "0.5rem" }}>
                      {reminderTypesQuery.data?.find((t) => t.id === selected.event.reminder_type_id)?.name}
                    </span>
                  )}
                </div>
                <div style={{ display: "flex", gap: "0.4rem", flexShrink: 0 }}>
                  <button
                    type="button"
                    className="btn icon-btn secondary icon-btn-edit"
                    onClick={() => setEditingEvent(selected.event)}
                    aria-label="Edit entry"
                  >
                    <Pencil size={15} />
                  </button>
                  <button
                    type="button"
                    className="btn icon-btn secondary icon-btn-delete"
                    onClick={() => setDeletingEvent(selected.event)}
                    aria-label="Delete entry"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
              {selected.event.text && <p style={{ marginBottom: 0 }}>{selected.event.text}</p>}
              {selected.event.photos.length > 0 && (
                <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.75rem" }}>
                  {selected.event.photos.map((p) => (
                    <img
                      key={p.id}
                      src={`/photos/${p.file_path}`}
                      alt=""
                      style={{ width: 90, height: 90, objectFit: "cover", borderRadius: "10px" }}
                    />
                  ))}
                </div>
              )}
            </>
          ) : (
            <div>
              <strong>{selected.plantName}</strong> · {selected.reminderTypeName}
              <div style={{ color: "var(--text-muted)", fontSize: "0.85rem", marginTop: "0.25rem" }}>
                {selected.date} — {selected.kind === "due" ? "Scheduled" : "Projected (assumes on-time completion)"}
              </div>
            </div>
          )}
        </div>
      )}

      <Modal open={createOpen} title="New timeline entry" onClose={() => setCreateOpen(false)}>
        <TimelineEntryForm
          plants={allPlants}
          reminderTypes={reminderTypesQuery.data ?? []}
          initialDate={createDate}
          onDone={() => setCreateOpen(false)}
        />
      </Modal>

      <Modal open={editingEvent !== null} title="Edit timeline entry" onClose={() => setEditingEvent(null)}>
        {editingEvent && (
          <TimelineEntryForm
            reminderTypes={reminderTypesQuery.data ?? []}
            existingEvent={editingEvent}
            onDone={() => {
              setEditingEvent(null);
              setSelected(null);
            }}
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
