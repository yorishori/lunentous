import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ListChecks, Plus } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { OneTimeReminder, Plant, ReminderState } from "../api/types";
import PlantCard from "../components/PlantCard";
import ConfirmDialog from "../components/ConfirmDialog";
import Skeleton from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { getIcon } from "../lib/icons";
import { hasDuplicateEntry } from "../lib/duplicateCheck";

/** Normalizes a regular reminder occurrence and a one-time informational
 * reminder into one shape the overdue/upcoming lists can render/sort
 * uniformly -- a one-time task has no type (icon/color stay null) and
 * completing it is a PATCH, not a logged timeline entry. */
interface DashboardTask {
  id: string;
  plantId: number;
  plantName: string;
  label: string;
  icon: string | null;
  color: string | null;
  daysOverdue: number;
  state?: ReminderState;
  oneTimeReminder?: OneTimeReminder;
}

export default function Dashboard() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [confirming, setConfirming] = useState<DashboardTask | null>(null);
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

  const { data: oneTimeReminders } = useQuery({
    queryKey: ["one-time-reminders"],
    queryFn: () => apiFetch<OneTimeReminder[]>("/one-time-reminders?completed=false"),
  });

  const markDone = useMutation({
    mutationFn: (task: DashboardTask) => {
      if (task.oneTimeReminder) {
        return apiFetch(`/one-time-reminders/${task.oneTimeReminder.id}`, {
          method: "PATCH",
          body: { completed_at: new Date().toISOString() },
        });
      }
      const state = task.state!;
      const formData = new FormData();
      formData.append("event_date", new Date().toISOString().slice(0, 10));
      formData.append("reminder_type_id", String(state.reminder_type_id));
      return apiFetch(`/plants/${state.plant_id}/timeline`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: (_data, task) => {
      queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
      queryClient.invalidateQueries({ queryKey: ["one-time-reminders"] });
      queryClient.invalidateQueries({ queryKey: ["plant", task.plantId] });
      queryClient.invalidateQueries({ queryKey: ["timeline", task.plantId] });
      showToast(task.oneTimeReminder ? "Reminder marked complete" : `${task.label} marked as done`, "success");
      closeConfirm();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to mark as done", "error"),
  });

  async function openConfirm(task: DashboardTask) {
    setConfirming(task);
    if (task.oneTimeReminder) return;
    setCheckingDuplicate(true);
    const today = new Date().toISOString().slice(0, 10);
    const duplicate = await hasDuplicateEntry(task.plantId, task.state!.reminder_type_id, today).catch(() => false);
    setCheckingDuplicate(false);
    setDuplicateExists(duplicate);
  }

  function closeConfirm() {
    setConfirming(null);
    setDuplicateExists(false);
  }

  const reminderTasks: DashboardTask[] = (states ?? [])
    .filter((s) => s.due_date)
    .map((s) => ({
      id: `reminder-${s.id}`,
      plantId: s.plant_id,
      plantName: s.plant_name ?? "",
      label: s.reminder_type_name ?? "",
      icon: s.reminder_type_icon ?? null,
      color: s.reminder_type_color ?? null,
      daysOverdue: s.days_overdue ?? -1,
      state: s,
    }));

  const oneTimeTasks: DashboardTask[] = (oneTimeReminders ?? []).map((r) => ({
    id: `one-time-${r.id}`,
    plantId: r.plant_id,
    plantName: r.plant_name ?? "",
    label: r.text,
    icon: null,
    color: null,
    daysOverdue: r.days_overdue ?? 0,
    oneTimeReminder: r,
  }));

  const due = [...reminderTasks, ...oneTimeTasks].sort((a, b) => b.daysOverdue - a.daysOverdue);

  const overdue = due.filter((t) => t.daysOverdue >= 0);
  const upcoming = due.filter((t) => t.daysOverdue < 0).slice(0, 8);

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
            {overdue.map((t) => (
              <TaskRow key={t.id} task={t} onMarkDone={() => openConfirm(t)} />
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
            {upcoming.map((t) => (
              <TaskRow key={t.id} task={t} onMarkDone={() => openConfirm(t)} />
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
        title={confirming?.oneTimeReminder ? "Mark complete?" : "Mark as done?"}
        message={
          confirming
            ? confirming.oneTimeReminder
              ? `This marks "${confirming.label}" complete for ${confirming.plantName}.`
              : duplicateExists
                ? `You already logged "${confirming.label}" for ${confirming.plantName} today. Mark it as done again anyway?`
                : `This logs "${confirming.label}" for ${confirming.plantName} today and recalculates its next due date.`
            : ""
        }
        confirmLabel={confirming?.oneTimeReminder ? "Mark complete" : "Mark as done"}
        pending={markDone.isPending || checkingDuplicate}
        onConfirm={() => confirming && markDone.mutate(confirming)}
        onCancel={closeConfirm}
      />
    </div>
  );
}

/** Untyped one-time reminders have no icon at all -- there's no type to
 * represent, unlike a regular reminder occurrence. */
function TaskRow({ task, onMarkDone }: { task: DashboardTask; onMarkDone: () => void }) {
  const Icon = getIcon(task.icon);
  const daysOverdue = task.daysOverdue;
  const isOverdue = daysOverdue >= 0;
  const label = daysOverdue === 0 ? "Due today" : daysOverdue > 0 ? `${daysOverdue}d overdue` : `in ${-daysOverdue}d`;

  return (
    <div className={`task-row${isOverdue ? " overdue" : ""}`}>
      <Link to={`/plants/${task.plantId}`} className="task-row-left" style={{ textDecoration: "none", color: "inherit" }}>
        <span
          style={{
            display: "inline-flex",
            width: "2rem",
            height: "2rem",
            borderRadius: "999px",
            background: task.color ? `${task.color}29` : "var(--accent-soft)",
            color: task.color ?? "var(--accent)",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
          }}
        >
          {Icon && <Icon size={16} />}
        </span>
        <span>
          <strong>{task.plantName}</strong> — {task.label}
        </span>
      </Link>
      <div className="task-row-actions">
        <span className={`badge ${isOverdue ? (daysOverdue === 0 ? "due-today" : "overdue") : "ok"}`}>{label}</span>
        <button
          type="button"
          className="btn icon-btn secondary icon-btn-done"
          onClick={onMarkDone}
          aria-label={task.oneTimeReminder ? "Mark complete" : "Mark as done"}
        >
          <CheckCircle2 size={15} />
        </button>
      </div>
    </div>
  );
}
