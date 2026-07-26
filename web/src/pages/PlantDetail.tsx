import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Sprout, MapPin, Calendar as CalendarIcon, Archive, ArchiveRestore } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type {
  Plant,
  PlantDetail as PlantDetailType,
  ReminderRule,
  ReminderType,
  PhaseType,
  PhaseWindow,
  TimelineEvent,
} from "../api/types";
import { useToast } from "../components/Toast";
import SlideOver from "../components/SlideOver";
import PlantForm from "../components/PlantForm";
import ReminderRuleForm from "../components/ReminderRuleForm";
import PhaseWindowForm from "../components/PhaseWindowForm";
import TimelineFeed from "../components/TimelineFeed";
import TimelineEntryForm from "../components/TimelineEntryForm";
import { getIcon } from "../lib/icons";

export default function PlantDetail() {
  const { id } = useParams<{ id: string }>();
  if (id === undefined) return <NewPlant />;
  return <ExistingPlant plantId={Number(id)} />;
}

function NewPlant() {
  const navigate = useNavigate();
  return (
    <div>
      <h1>Add plant</h1>
      <div className="card" style={{ maxWidth: 480 }}>
        <PlantForm onDone={(plant: Plant) => navigate(`/plants/${plant.id}`, { replace: true })} />
      </div>
    </div>
  );
}

type SlideoverState =
  | { type: "none" }
  | { type: "plant" }
  | { type: "rule"; rule?: ReminderRule }
  | { type: "window"; window?: PhaseWindow }
  | { type: "timeline-entry"; event?: TimelineEvent };

function ExistingPlant({ plantId }: { plantId: number }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [slideover, setSlideover] = useState<SlideoverState>({ type: "none" });

  const plantQuery = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => apiFetch<PlantDetailType>(`/plants/${plantId}`),
  });

  const reminderTypesQuery = useQuery({
    queryKey: ["reminder-types", { archived: false }],
    queryFn: () => apiFetch<ReminderType[]>("/reminder-types?archived=false"),
  });

  const phaseTypesQuery = useQuery({
    queryKey: ["phase-types", { archived: false }],
    queryFn: () => apiFetch<PhaseType[]>("/phase-types?archived=false"),
  });

  const rulesQuery = useQuery({
    queryKey: ["reminder-rules", plantId],
    queryFn: () => apiFetch<ReminderRule[]>(`/plants/${plantId}/reminder-rules`),
  });

  const windowsQuery = useQuery({
    queryKey: ["phase-windows", plantId],
    queryFn: () => apiFetch<PhaseWindow[]>(`/plants/${plantId}/phase-windows`),
  });

  const archiveMutation = useMutation({
    mutationFn: (action: "archive" | "unarchive") => apiFetch(`/plants/${plantId}/${action}`, { method: "POST" }),
    onSuccess: (_data, action) => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      showToast(action === "archive" ? "Plant archived" : "Plant unarchived", "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Something went wrong", "error"),
  });

  const closeSlideover = () => setSlideover({ type: "none" });

  if (plantQuery.isLoading || !plantQuery.data) return <p>Loading…</p>;
  const plant = plantQuery.data;

  const usedReminderTypeIds = new Set((rulesQuery.data ?? []).map((r) => r.reminder_type_id));
  const availableReminderTypes = (reminderTypesQuery.data ?? []).filter((t) => !usedReminderTypeIds.has(t.id));

  return (
    <div>
      <button type="button" className="btn secondary" onClick={() => navigate(-1)} style={{ marginBottom: "1.25rem" }}>
        ← Back
      </button>

      <div className="card plant-hero" style={{ marginBottom: "1.75rem" }}>
        {plant.avatar_photo_path ? (
          <img src={`/photos/${plant.avatar_photo_path}`} alt="" className="plant-hero-photo" />
        ) : (
          <div className="plant-hero-photo-placeholder">
            <Sprout size={40} />
          </div>
        )}
        <div className="plant-hero-info">
          <h1>{plant.name}</h1>
          <div className="plant-hero-meta">
            {plant.species && <span>{plant.species}</span>}
            {plant.location && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: "0.3rem" }}>
                <MapPin size={14} /> {plant.location}
              </span>
            )}
            {plant.acquired_date && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: "0.3rem" }}>
                <CalendarIcon size={14} /> since {plant.acquired_date}
              </span>
            )}
          </div>
          {plant.general_notes && <p style={{ color: "var(--text-muted)" }}>{plant.general_notes}</p>}
          {plant.active_phase_windows.map((w) => (
            <span key={w.id} className="badge neutral" style={{ marginRight: "0.35rem" }}>
              {w.phase_type_name}
            </span>
          ))}
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}>
            <button type="button" className="btn secondary" onClick={() => setSlideover({ type: "plant" })}>
              <Pencil size={15} /> Edit
            </button>
            <button
              type="button"
              className="btn secondary"
              onClick={() => archiveMutation.mutate(plant.archived ? "unarchive" : "archive")}
            >
              {plant.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />}
              {plant.archived ? "Unarchive" : "Archive"}
            </button>
          </div>
        </div>
      </div>

      <section style={{ marginBottom: "1.75rem" }}>
        <div className="section-header">
          <h2>Phase windows</h2>
          <button type="button" className="btn secondary" onClick={() => setSlideover({ type: "window" })}>
            <Plus size={15} /> Add
          </button>
        </div>
        {(windowsQuery.data ?? []).map((w) => (
          <div key={w.id} className="item-row">
            <div className="item-row-main">
              <span
                style={{
                  width: "0.6rem",
                  height: "0.6rem",
                  borderRadius: "999px",
                  background: w.phase_type_color ?? "var(--accent)",
                  flexShrink: 0,
                }}
              />
              <div>
                <div style={{ fontWeight: 600 }}>{w.phase_type_name}</div>
                <div style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>
                  {w.start_month}/{w.start_day} – {w.end_month}/{w.end_day}
                </div>
              </div>
            </div>
            <div className="item-row-actions">
              <button
                type="button"
                className="btn icon-btn secondary icon-btn-edit"
                onClick={() => setSlideover({ type: "window", window: w })}
                aria-label="Edit phase window"
              >
                <Pencil size={14} />
              </button>
            </div>
          </div>
        ))}
        {(windowsQuery.data ?? []).length === 0 && <p style={{ color: "var(--text-muted)" }}>No phase windows yet.</p>}
      </section>

      <section style={{ marginBottom: "1.75rem" }}>
        <div className="section-header">
          <h2>Reminder rules</h2>
          {availableReminderTypes.length > 0 && (
            <button type="button" className="btn secondary" onClick={() => setSlideover({ type: "rule" })}>
              <Plus size={15} /> Add
            </button>
          )}
        </div>
        {(rulesQuery.data ?? []).map((rule) => {
          const type = reminderTypesQuery.data?.find((t) => t.id === rule.reminder_type_id);
          const Icon = getIcon(type?.icon);
          return (
            <div key={rule.id} className="item-row">
              <div className="item-row-main">
                <span
                  style={{
                    display: "inline-flex",
                    width: "2rem",
                    height: "2rem",
                    borderRadius: "999px",
                    background: type?.color ? `${type.color}29` : "var(--accent-soft)",
                    color: type?.color ?? "var(--accent)",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  {Icon && <Icon size={16} />}
                </span>
                <div>
                  <div style={{ fontWeight: 600 }}>{type?.name}</div>
                  <div style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>
                    {rule.default_interval_days ? `Every ${rule.default_interval_days} days` : "Paused by default"}
                    {rule.override_periods.length > 0 &&
                      ` · ${rule.override_periods.length} override${rule.override_periods.length === 1 ? "" : "s"}`}
                  </div>
                </div>
              </div>
              <div className="item-row-actions">
                <button
                  type="button"
                  className="btn icon-btn secondary icon-btn-edit"
                  onClick={() => setSlideover({ type: "rule", rule })}
                  aria-label="Edit reminder rule"
                >
                  <Pencil size={14} />
                </button>
              </div>
            </div>
          );
        })}
        {(rulesQuery.data ?? []).length === 0 && <p style={{ color: "var(--text-muted)" }}>No reminder rules yet.</p>}
      </section>

      <section>
        <div className="section-header">
          <h2>Timeline</h2>
          <button type="button" className="btn secondary" onClick={() => setSlideover({ type: "timeline-entry" })}>
            <Plus size={15} /> Log entry
          </button>
        </div>
        <TimelineFeed
          plantId={plantId}
          reminderTypes={reminderTypesQuery.data ?? []}
          onEdit={(event) => setSlideover({ type: "timeline-entry", event })}
        />
      </section>

      <SlideOver open={slideover.type === "plant"} title="Edit plant" onClose={closeSlideover}>
        <PlantForm plant={plant} onDone={closeSlideover} />
      </SlideOver>

      <SlideOver
        open={slideover.type === "window"}
        title={slideover.type === "window" && slideover.window ? "Edit phase window" : "Add phase window"}
        onClose={closeSlideover}
      >
        {slideover.type === "window" && (
          <PhaseWindowForm
            plantId={plantId}
            phaseTypes={phaseTypesQuery.data ?? []}
            existingWindow={slideover.window}
            onDone={closeSlideover}
          />
        )}
      </SlideOver>

      <SlideOver
        open={slideover.type === "rule"}
        title={slideover.type === "rule" && slideover.rule ? "Edit reminder rule" : "Add reminder rule"}
        onClose={closeSlideover}
      >
        {slideover.type === "rule" && (
          <ReminderRuleForm
            plantId={plantId}
            reminderTypes={slideover.rule ? (reminderTypesQuery.data ?? []) : availableReminderTypes}
            existingRule={slideover.rule}
            onDone={closeSlideover}
          />
        )}
      </SlideOver>

      <SlideOver
        open={slideover.type === "timeline-entry"}
        title={slideover.type === "timeline-entry" && slideover.event ? "Edit timeline entry" : "Log timeline entry"}
        onClose={closeSlideover}
      >
        {slideover.type === "timeline-entry" && (
          <TimelineEntryForm
            plantId={plantId}
            reminderTypes={reminderTypesQuery.data ?? []}
            existingEvent={slideover.event}
            onDone={closeSlideover}
          />
        )}
      </SlideOver>
    </div>
  );
}
