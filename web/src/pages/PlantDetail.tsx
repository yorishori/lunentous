import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type {
  Plant,
  PlantDetail as PlantDetailType,
  ReminderRule,
  ReminderType,
  PhaseType,
  PhaseWindow,
} from "../api/types";
import ReminderRuleForm from "../components/ReminderRuleForm";
import PhaseWindowForm from "../components/PhaseWindowForm";
import TimelineFeed from "../components/TimelineFeed";
import TimelineEntryForm from "../components/TimelineEntryForm";

export default function PlantDetail() {
  const { id } = useParams<{ id: string }>();
  const isNew = id === undefined;
  const plantId = id ? Number(id) : undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const plantQuery = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => apiFetch<PlantDetailType>(`/plants/${plantId}`),
    enabled: !isNew,
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
    enabled: !isNew,
  });

  const windowsQuery = useQuery({
    queryKey: ["phase-windows", plantId],
    queryFn: () => apiFetch<PhaseWindow[]>(`/plants/${plantId}/phase-windows`),
    enabled: !isNew,
  });

  const [name, setName] = useState("");
  const [species, setSpecies] = useState("");
  const [location, setLocation] = useState("");
  const [acquiredDate, setAcquiredDate] = useState("");
  const [generalNotes, setGeneralNotes] = useState("");
  const [showAddRule, setShowAddRule] = useState(false);
  const [showAddWindow, setShowAddWindow] = useState(false);

  useEffect(() => {
    if (plantQuery.data) {
      setName(plantQuery.data.name);
      setSpecies(plantQuery.data.species ?? "");
      setLocation(plantQuery.data.location ?? "");
      setAcquiredDate(plantQuery.data.acquired_date ?? "");
      setGeneralNotes(plantQuery.data.general_notes ?? "");
    }
  }, [plantQuery.data]);

  const savePlant = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        species: species || null,
        location: location || null,
        acquired_date: acquiredDate || null,
        general_notes: generalNotes || null,
      };
      return isNew
        ? apiFetch<Plant>("/plants", { method: "POST", body })
        : apiFetch<Plant>(`/plants/${plantId}`, { method: "PATCH", body });
    },
    onSuccess: (plant) => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      if (isNew) {
        navigate(`/plants/${plant.id}`, { replace: true });
      } else {
        queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      }
    },
  });

  const archiveMutation = useMutation({
    mutationFn: (action: "archive" | "unarchive") =>
      apiFetch(`/plants/${plantId}/${action}`, { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
    },
  });

  const avatarUpload = useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return apiFetch(`/plants/${plantId}/avatar`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    savePlant.mutate();
  }

  if (!isNew && plantQuery.isLoading) return <p>Loading…</p>;

  const usedReminderTypeIds = new Set((rulesQuery.data ?? []).map((r) => r.reminder_type_id));
  const availableReminderTypes = (reminderTypesQuery.data ?? []).filter((t) => !usedReminderTypeIds.has(t.id));

  return (
    <div>
      <button type="button" className="btn secondary" onClick={() => navigate(-1)} style={{ marginBottom: "1rem" }}>
        ← Back
      </button>

      <form onSubmit={handleSubmit} className="card" style={{ marginBottom: "1.5rem" }}>
        <h1 style={{ marginTop: 0 }}>{isNew ? "Add plant" : name || "Plant"}</h1>
        <div className="form-row">
          <label>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Species</label>
          <input value={species} onChange={(e) => setSpecies(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Location</label>
          <input value={location} onChange={(e) => setLocation(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Acquired date</label>
          <input type="date" value={acquiredDate} onChange={(e) => setAcquiredDate(e.target.value)} />
        </div>
        <div className="form-row">
          <label>General notes</label>
          <textarea value={generalNotes} onChange={(e) => setGeneralNotes(e.target.value)} rows={3} />
        </div>
        {!isNew && (
          <div className="form-row">
            <label>Avatar photo</label>
            <input
              type="file"
              accept="image/*"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) avatarUpload.mutate(file);
              }}
            />
          </div>
        )}
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <button type="submit" className="btn" disabled={savePlant.isPending}>
            {isNew ? "Create plant" : "Save changes"}
          </button>
          {!isNew && plantQuery.data && (
            <button
              type="button"
              className="btn secondary"
              onClick={() => archiveMutation.mutate(plantQuery.data!.archived ? "unarchive" : "archive")}
            >
              {plantQuery.data.archived ? "Unarchive" : "Archive"}
            </button>
          )}
        </div>
        {savePlant.isError && <p style={{ color: "var(--overdue)" }}>{(savePlant.error as ApiError).message}</p>}
      </form>

      {!isNew && plantId !== undefined && (
        <>
          <section style={{ marginBottom: "1.5rem" }}>
            <h2>Phase windows</h2>
            {(windowsQuery.data ?? []).map((w) => (
              <PhaseWindowForm key={w.id} plantId={plantId} phaseTypes={phaseTypesQuery.data ?? []} existingWindow={w} />
            ))}
            {showAddWindow ? (
              <PhaseWindowForm
                plantId={plantId}
                phaseTypes={phaseTypesQuery.data ?? []}
                onDone={() => setShowAddWindow(false)}
              />
            ) : (
              <button type="button" className="btn secondary" onClick={() => setShowAddWindow(true)}>
                + Add phase window
              </button>
            )}
          </section>

          <section style={{ marginBottom: "1.5rem" }}>
            <h2>Reminder rules</h2>
            {(rulesQuery.data ?? []).map((rule) => (
              <ReminderRuleForm
                key={rule.id}
                plantId={plantId}
                reminderTypes={reminderTypesQuery.data ?? []}
                existingRule={rule}
              />
            ))}
            {availableReminderTypes.length > 0 &&
              (showAddRule ? (
                <ReminderRuleForm
                  plantId={plantId}
                  reminderTypes={availableReminderTypes}
                  onDone={() => setShowAddRule(false)}
                />
              ) : (
                <button type="button" className="btn secondary" onClick={() => setShowAddRule(true)}>
                  + Add reminder rule
                </button>
              ))}
          </section>

          <section>
            <h2>Timeline</h2>
            <TimelineEntryForm plantId={plantId} reminderTypes={reminderTypesQuery.data ?? []} />
            <TimelineFeed plantId={plantId} reminderTypes={reminderTypesQuery.data ?? []} />
          </section>
        </>
      )}
    </div>
  );
}
