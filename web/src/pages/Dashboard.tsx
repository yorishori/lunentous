import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ListChecks } from "lucide-react";
import { apiFetch } from "../api/client";
import type { Plant, ReminderState } from "../api/types";
import PlantCard from "../components/PlantCard";
import { getIcon } from "../lib/icons";

export default function Dashboard() {
  const {
    data: plants,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["plants", { archived: false }],
    queryFn: () => apiFetch<Plant[]>("/plants?archived=false"),
  });

  const { data: states } = useQuery({
    queryKey: ["reminder-states"],
    queryFn: () => apiFetch<ReminderState[]>("/reminder-states"),
  });

  const due = (states ?? [])
    .filter((s) => s.due_date)
    .sort((a, b) => (b.days_overdue ?? -Infinity) - (a.days_overdue ?? -Infinity));

  const overdue = due.filter((s) => (s.days_overdue ?? -1) >= 0);
  const upcoming = due.filter((s) => (s.days_overdue ?? -1) < 0).slice(0, 8);

  if (isLoading) return <p>Loading plants…</p>;
  if (error) return <p>Failed to load plants.</p>;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
        <Link to="/plants/new" className="btn">
          Add plant
        </Link>
      </div>

      {overdue.length > 0 && (
        <section style={{ marginBottom: "1.75rem" }}>
          <div className="section-header">
            <h2 style={{ display: "flex", alignItems: "center", gap: "0.5rem", color: "var(--overdue)" }}>
              <AlertTriangle size={18} /> Overdue
            </h2>
          </div>
          <div className="task-list">
            {overdue.map((s) => (
              <TaskRow key={s.id} state={s} />
            ))}
          </div>
        </section>
      )}

      {upcoming.length > 0 && (
        <section style={{ marginBottom: "2rem" }}>
          <div className="section-header">
            <h2 style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <ListChecks size={18} /> Next tasks
            </h2>
          </div>
          <div className="task-list">
            {upcoming.map((s) => (
              <TaskRow key={s.id} state={s} />
            ))}
          </div>
        </section>
      )}

      <div className="section-header">
        <h2 style={{ margin: 0 }}>Plants</h2>
      </div>
      {plants && plants.length === 0 && <p>No plants yet. Add your first one.</p>}
      <div className="card-grid">
        {plants?.map((plant) => (
          <PlantCard key={plant.id} plant={plant} />
        ))}
      </div>
    </div>
  );
}

function TaskRow({ state }: { state: ReminderState }) {
  const Icon = getIcon(state.reminder_type_icon);
  const daysOverdue = state.days_overdue ?? -1;
  const isOverdue = daysOverdue >= 0;
  const label = daysOverdue === 0 ? "Due today" : daysOverdue > 0 ? `${daysOverdue}d overdue` : `in ${-daysOverdue}d`;

  return (
    <Link to={`/plants/${state.plant_id}`} className={`task-row${isOverdue ? " overdue" : ""}`}>
      <div className="task-row-left">
        <span
          style={{
            display: "inline-flex",
            width: "2rem",
            height: "2rem",
            borderRadius: "999px",
            background: state.reminder_type_color ? `${state.reminder_type_color}29` : "var(--accent-soft)",
            color: state.reminder_type_color ?? "var(--accent)",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
          }}
        >
          {Icon && <Icon size={16} />}
        </span>
        <span>
          <strong>{state.plant_name}</strong> — {state.reminder_type_name}
        </span>
      </div>
      <span className={`badge ${isOverdue ? (daysOverdue === 0 ? "due-today" : "overdue") : "ok"}`}>{label}</span>
    </Link>
  );
}
