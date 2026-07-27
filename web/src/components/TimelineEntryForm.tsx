import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { X } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { Photo, Plant, ReminderType, TimelineEvent } from "../api/types";
import { useToast } from "./Toast";
import ConfirmDialog from "./ConfirmDialog";
import Spinner from "./Spinner";
import { hasDuplicateEntry } from "../lib/duplicateCheck";

interface Props {
  /** Fixed plant context (plant detail page). Mutually exclusive with `plants`. */
  plantId?: number;
  /** Selectable plant list (calendar "new entry" flow, plant unknown up front). */
  plants?: Plant[];
  reminderTypes: ReminderType[];
  existingEvent?: TimelineEvent;
  initialDate?: string;
  onDone: () => void;
}

export default function TimelineEntryForm({
  plantId,
  plants,
  reminderTypes,
  existingEvent,
  initialDate,
  onDone,
}: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [selectedPlantId, setSelectedPlantId] = useState<number>(
    existingEvent?.plant_id ?? plantId ?? plants?.[0]?.id ?? 0
  );
  const [eventDate, setEventDate] = useState(
    existingEvent?.event_date ?? initialDate ?? new Date().toISOString().slice(0, 10)
  );
  const [reminderTypeId, setReminderTypeId] = useState<string>(
    existingEvent?.reminder_type_id != null ? String(existingEvent.reminder_type_id) : ""
  );
  const [text, setText] = useState(existingEvent?.text ?? "");
  const [files, setFiles] = useState<FileList | null>(null);
  const [existingPhotos, setExistingPhotos] = useState<Photo[]>(existingEvent?.photos ?? []);
  const [duplicateWarning, setDuplicateWarning] = useState(false);
  const [checkingDuplicate, setCheckingDuplicate] = useState(false);

  function invalidate(forPlantId: number) {
    queryClient.invalidateQueries({ queryKey: ["timeline", forPlantId] });
    queryClient.invalidateQueries({ queryKey: ["plant", forPlantId] });
    queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
  }

  const save = useMutation({
    mutationFn: async () => {
      if (existingEvent) {
        await apiFetch(`/timeline/${existingEvent.id}`, {
          method: "PATCH",
          body: {
            event_date: eventDate,
            reminder_type_id: reminderTypeId ? Number(reminderTypeId) : null,
            text: text || null,
          },
        });
        if (files && files.length > 0) {
          const formData = new FormData();
          for (const file of Array.from(files)) formData.append("photo", file);
          await apiFetch(`/timeline/${existingEvent.id}/photos`, {
            method: "POST",
            body: formData,
            isFormData: true,
          });
        }
        return;
      }

      const formData = new FormData();
      formData.append("event_date", eventDate);
      if (reminderTypeId) formData.append("reminder_type_id", reminderTypeId);
      if (text) formData.append("text", text);
      if (files) {
        for (const file of Array.from(files)) formData.append("photo", file);
      }
      return apiFetch(`/plants/${selectedPlantId}/timeline`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: () => {
      invalidate(existingEvent?.plant_id ?? selectedPlantId);
      showToast(existingEvent ? "Timeline entry updated" : "Timeline entry added", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to save timeline entry", "error"),
  });

  const deletePhoto = useMutation({
    mutationFn: (photoId: number) => apiFetch(`/photos/${photoId}`, { method: "DELETE" }),
    onSuccess: (_data, photoId) => {
      setExistingPhotos((prev) => prev.filter((p) => p.id !== photoId));
      invalidate(existingEvent?.plant_id ?? selectedPlantId);
      showToast("Photo removed", "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to remove photo", "error"),
  });

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!existingEvent && reminderTypeId) {
      setCheckingDuplicate(true);
      const duplicate = await hasDuplicateEntry(selectedPlantId, Number(reminderTypeId), eventDate).catch(
        () => false
      );
      setCheckingDuplicate(false);
      if (duplicate) {
        setDuplicateWarning(true);
        return;
      }
    }
    save.mutate();
  }

  return (
    <form onSubmit={handleSubmit}>
      {plants && !existingEvent && (
        <div className="form-row">
          <label>Plant</label>
          <select value={selectedPlantId} onChange={(e) => setSelectedPlantId(Number(e.target.value))} required>
            {plants.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="form-row">
        <label>Date</label>
        <input type="date" value={eventDate} onChange={(e) => setEventDate(e.target.value)} required />
      </div>
      <div className="form-row">
        <label>Reminder type (optional — tags this entry as completing a reminder)</label>
        <select value={reminderTypeId} onChange={(e) => setReminderTypeId(e.target.value)}>
          <option value="">Journal note only</option>
          {reminderTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label>Notes</label>
        <textarea value={text} onChange={(e) => setText(e.target.value)} rows={3} />
      </div>

      {existingPhotos.length > 0 && (
        <div className="form-row">
          <label>Existing photos</label>
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            {existingPhotos.map((p) => (
              <div key={p.id} style={{ position: "relative" }}>
                <img
                  src={`/photos/${p.file_path}`}
                  alt=""
                  style={{ width: 72, height: 72, objectFit: "cover", borderRadius: "10px" }}
                />
                <button
                  type="button"
                  className="btn icon-btn secondary icon-btn-delete"
                  style={{ position: "absolute", top: -8, right: -8, padding: "0.25rem" }}
                  onClick={() => deletePhoto.mutate(p.id)}
                  aria-label="Remove photo"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="form-row">
        <label>{existingEvent ? "Add more photos" : "Photos"}</label>
        <input type="file" accept="image/*" multiple onChange={(e) => setFiles(e.target.files)} />
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
      <button
        type="submit"
        className="btn"
        disabled={save.isPending || checkingDuplicate}
        style={{ width: "100%" }}
      >
        {(save.isPending || checkingDuplicate) && <Spinner size={14} />} {existingEvent ? "Save changes" : "Add entry"}
      </button>

      <ConfirmDialog
        open={duplicateWarning}
        title="Already logged today?"
        message={`This plant already has a "${reminderTypes.find((t) => t.id === Number(reminderTypeId))?.name}" entry on ${eventDate}. Add another one anyway?`}
        confirmLabel="Add anyway"
        pending={save.isPending}
        onConfirm={() => {
          setDuplicateWarning(false);
          save.mutate();
        }}
        onCancel={() => setDuplicateWarning(false)}
      />
    </form>
  );
}
