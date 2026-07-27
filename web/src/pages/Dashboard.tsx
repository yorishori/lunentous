import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ListChecks, Plus } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { Plant, ReminderState } from "../api/types";
import PlantCard from "../components/PlantCard";
import ConfirmDialog from "../components/ConfirmDialog";
import Skeleton from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { getIcon } from "../lib/icons";
import { hasDuplicateEntry } from "../lib/duplicateCheck";

export default function Dashboard() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [confirming, setConfirming] = useState<ReminderState | null>(null);
  const [duplicateExists, setDuplicateExists] = useState(false);
  const [checkingDuplicate, setCheckingDuplicate] = useState(false);

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

  const markDone = useMutation({
    mutationFn: (state: ReminderState) => {
      const formData = new FormData();
      formData.append("event_date", new Date().toISOString().slice(0, 10));
      formData.append("reminder_type_id", String(state.reminder_type_id));
      return apiFetch(`/plants/${state.plant_id}/timeline`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: (_data, state) => {
      queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
      queryClient.invalidateQueries({ queryKey: ["plant", state.plant_id] });
      queryClient.invalidateQueries({ queryKey: ["timeline", state.plant_id] });
      showToast(`${state.reminder_type_name} marked as done`, "success");
      closeConfirm();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to mark as done", "error"),
  });

  async function openConfirm(state: ReminderState) {
    setConfirming(state);
    setCheckingDuplicate(true);
    const today = new Date().toISOString().slice(0, 10);
    const duplicate = await hasDuplicateEntry(state.plant_id, state.reminder_type_id, today).catch(() => false);
    setCheckingDuplicate(false);
    setDuplicateExists(duplicate);
  }

  function closeConfirm() {
    setConfirming(null);
    setDuplicateExists(false);
  }

  const due = (states ?? [])
    .filter((s) => s.due_date)
    .sort((a, b) => (b.days_overdue ?? -Infinity) - (a.days_overdue ?? -Infinity));

  const overdue = due.filter((s) => (s.days_overdue ?? -1) >= 0);
  const upcoming = due.filter((s) => (s.days_overdue ?? -1) < 0).slice(0, 8);

  if (isLoading) {
    return (
      <div className="card-grid">
        {[0, 1, 2].map((i) => (
          <div key={i} className="card">
            <div style={{ display: "flex", gap: "0.85rem", alignItems: "center" }}>
              <Skeleton width={48} height={48} style={{ borderRadius: "999px" }} />
              <div style={{ flex: 1 }}>
                <Skeleton width="70%" height="1.1rem" style={{ marginBottom: "0.4rem" }} />
                <Skeleton width="45%" height="0.85rem" />
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  }
  if (error) return <p>Failed to load plants.</p>;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
        <Link to="/plants/new" className="btn">
          <Plus size={16} /> Add plant
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
              <TaskRow key={s.id} state={s} onMarkDone={() => openConfirm(s)} />
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
              <TaskRow key={s.id} state={s} onMarkDone={() => openConfirm(s)} />
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

      <ConfirmDialog
        open={confirming !== null}
        title="Mark as done?"
        message={
          confirming
            ? duplicateExists
              ? `You already logged "${confirming.reminder_type_name}" for ${confirming.plant_name} today. Mark it as done again anyway?`
              : `This logs "${confirming.reminder_type_name}" for ${confirming.plant_name} today and recalculates its next due date.`
            : ""
        }
        confirmLabel="Mark as done"
        pending={markDone.isPending || checkingDuplicate}
        onConfirm={() => confirming && markDone.mutate(confirming)}
        onCancel={closeConfirm}
      />
    </div>
  );
}

function TaskRow({ state, onMarkDone }: { state: ReminderState; onMarkDone: () => void }) {
  const Icon = getIcon(state.reminder_type_icon);
  const daysOverdue = state.days_overdue ?? -1;
  const isOverdue = daysOverdue >= 0;
  const label = daysOverdue === 0 ? "Due today" : daysOverdue > 0 ? `${daysOverdue}d overdue` : `in ${-daysOverdue}d`;

  return (
    <div className={`task-row${isOverdue ? " overdue" : ""}`}>
      <Link to={`/plants/${state.plant_id}`} className="task-row-left" style={{ textDecoration: "none", color: "inherit" }}>
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
      </Link>
      <div className="task-row-actions">
        <span className={`badge ${isOverdue ? (daysOverdue === 0 ? "due-today" : "overdue") : "ok"}`}>{label}</span>
        <button type="button" className="btn icon-btn secondary icon-btn-done" onClick={onMarkDone} aria-label="Mark as done">
          <CheckCircle2 size={15} />
        </button>
      </div>
    </div>
  );
}
