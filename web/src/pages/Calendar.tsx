import { useMemo, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import type { Plant, ReminderRule, ReminderState } from "../api/types";
import { projectFutureOccurrences } from "../lib/dateMath";
import CalendarGrid, { type CalendarMarker } from "../components/CalendarGrid";

const PROJECTION_COUNT = 6;

interface GlobalReminderState extends ReminderState {
  plant_name: string;
}

export default function Calendar() {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);

  const plantsQuery = useQuery({
    queryKey: ["plants", { archived: false }],
    queryFn: () => apiFetch<Plant[]>("/plants?archived=false"),
  });

  const statesQuery = useQuery({
    queryKey: ["reminder-states"],
    queryFn: () => apiFetch<GlobalReminderState[]>("/reminder-states"),
  });

  const plants = plantsQuery.data ?? [];

  const ruleQueries = useQueries({
    queries: plants.map((plant) => ({
      queryKey: ["reminder-rules", plant.id],
      queryFn: () => apiFetch<ReminderRule[]>(`/plants/${plant.id}/reminder-rules`),
    })),
  });

  const rulesByPlantAndType = new Map<string, ReminderRule>();
  for (const q of ruleQueries) {
    for (const rule of q.data ?? []) {
      rulesByPlantAndType.set(`${rule.plant_id}:${rule.reminder_type_id}`, rule);
    }
  }

  const markers = useMemo(() => {
    const map = new Map<string, CalendarMarker[]>();
    if (!statesQuery.data) return map;

    const add = (date: string, marker: CalendarMarker) => {
      const list = map.get(date) ?? [];
      list.push(marker);
      map.set(date, list);
    };

    for (const state of statesQuery.data) {
      if (!state.due_date) continue;
      const rule = rulesByPlantAndType.get(`${state.plant_id}:${state.reminder_type_id}`);

      add(state.due_date, {
        plantName: state.plant_name ?? "",
        reminderTypeName: state.reminder_type_name ?? "",
        projected: false,
      });

      if (rule) {
        const projected = projectFutureOccurrences(
          state.due_date,
          rule.default_interval_days,
          rule.override_periods,
          PROJECTION_COUNT
        );
        for (const date of projected) {
          add(date, {
            plantName: state.plant_name ?? "",
            reminderTypeName: state.reminder_type_name ?? "",
            projected: true,
          });
        }
      }
    }
    return map;
  }, [statesQuery.data, rulesByPlantAndType]);

  function prevMonth() {
    if (viewMonth === 1) {
      setViewYear((y) => y - 1);
      setViewMonth(12);
    } else {
      setViewMonth((m) => m - 1);
    }
  }

  function nextMonth() {
    if (viewMonth === 12) {
      setViewYear((y) => y + 1);
      setViewMonth(1);
    } else {
      setViewMonth((m) => m + 1);
    }
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
        <h1 style={{ margin: 0 }}>Calendar</h1>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
          <button type="button" className="btn secondary" onClick={prevMonth}>
            ←
          </button>
          <strong>{new Date(viewYear, viewMonth - 1, 1).toLocaleString(undefined, { month: "long", year: "numeric" })}</strong>
          <button type="button" className="btn secondary" onClick={nextMonth}>
            →
          </button>
        </div>
      </div>
      <p style={{ color: "var(--text-muted)", marginTop: 0 }}>
        Solid markers are real scheduled due dates; dashed markers are projected future occurrences assuming
        on-time completion, and are never written to the database.
      </p>
      <CalendarGrid year={viewYear} month={viewMonth} markersByDate={markers} />
    </div>
  );
}
